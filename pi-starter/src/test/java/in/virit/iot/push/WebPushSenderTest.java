package in.virit.iot.push;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delivery against a stand-in push service on localhost: the message goes out
 * signed with our VAPID key and encrypted for the browser's keys, and a push
 * service answering 410 Gone makes the subscription disappear.
 */
class WebPushSenderTest {

    @TempDir
    Path dir;
    private HttpServer pushService;
    private volatile int status = 201;
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private CountDownLatch delivered = new CountDownLatch(1);
    private PushSubscriptionStore store;
    private WebPushSender sender;

    @BeforeEach
    void start() throws Exception {
        pushService = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pushService.createContext("/push", exchange -> {
            received.put("authorization", String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
            received.put("encoding", String.valueOf(exchange.getRequestHeaders().getFirst("Content-Encoding")));
            received.put("bytes", String.valueOf(exchange.getRequestBody().readAllBytes().length));
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            delivered.countDown();
        });
        pushService.start();

        var keys = new VapidKeys();
        keys.dir = dir.toString();
        keys.configuredPublicKey = Optional.empty();
        keys.configuredPrivateKey = Optional.empty();
        keys.subject = "mailto:test@example.org";
        keys.load();
        store = new PushSubscriptionStore();
        store.dir = dir.toString();
        store.load();
        sender = new WebPushSender();
        sender.keys = keys;
        sender.store = store;
        sender.init();
    }

    @AfterEach
    void stop() {
        sender.stop();
        pushService.stop(0);
    }

    /** A subscription as a browser would hand it over: its own P-256 key and auth secret. */
    private AlertSubscription browserSubscription() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var point = ((ECPublicKey) generator.generateKeyPair().getPublic()).getW();
        byte[] uncompressed = new byte[65];
        uncompressed[0] = 0x04;
        copy32(point.getAffineX().toByteArray(), uncompressed, 1);
        copy32(point.getAffineY().toByteArray(), uncompressed, 33);
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        var base64 = Base64.getUrlEncoder().withoutPadding();
        String endpoint = "http://127.0.0.1:" + pushService.getAddress().getPort() + "/push/abc";
        return new AlertSubscription("phone-1", endpoint, base64.encodeToString(uncompressed),
                base64.encodeToString(auth), 18.0, 25.0, null, Instant.now());
    }

    private static void copy32(byte[] value, byte[] target, int offset) {
        int length = Math.min(value.length, 32);
        System.arraycopy(value, value.length - length, target, offset + 32 - length, length);
    }

    @Test
    void sendsASignedEncryptedMessage() throws Exception {
        var subscription = browserSubscription();
        store.save(subscription);
        sender.send(subscription, "Pi Starter: too warm", "25.4 °C, above your max of 25 °C");
        assertTrue(delivered.await(10, TimeUnit.SECONDS), "the push service was called");
        assertTrue(received.get("authorization").startsWith("vapid "), received.get("authorization"));
        assertEquals("aes128gcm", received.get("encoding"));
        assertTrue(Integer.parseInt(received.get("bytes")) > 0, "an encrypted payload");
        assertTrue(store.find("phone-1").isPresent(), "a delivered subscription stays");
    }

    @Test
    void forgetsASubscriptionThePushServiceNoLongerKnows() throws Exception {
        status = 410;
        var subscription = browserSubscription();
        store.save(subscription);
        sender.send(subscription, "t", "b");
        assertTrue(delivered.await(10, TimeUnit.SECONDS));
        for (int i = 0; i < 50 && store.find("phone-1").isPresent(); i++) {
            Thread.sleep(100);
        }
        assertFalse(store.find("phone-1").isPresent(), "410 Gone removes it");
    }
}
