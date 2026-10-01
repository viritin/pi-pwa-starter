package in.virit.iot.push;

import com.vaadin.flow.server.webpush.WebPush;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated VAPID pair: the right shape, accepted by Vaadin, and stable across restarts. */
class VapidKeysTest {

    @TempDir
    Path dir;

    private VapidKeys open() {
        var keys = new VapidKeys();
        keys.dir = dir.toString();
        keys.configuredPublicKey = Optional.empty();
        keys.configuredPrivateKey = Optional.empty();
        keys.subject = "mailto:test@example.org";
        keys.load();
        return keys;
    }

    @Test
    void generatesAPairVaadinAccepts() {
        var keys = open();
        assertEquals(65, Base64.getUrlDecoder().decode(keys.publicKey()).length, "uncompressed P-256 point");
        assertEquals(32, Base64.getUrlDecoder().decode(keys.privateKey()).length, "private scalar");
        assertDoesNotThrow(() -> new WebPush(keys.publicKey(), keys.privateKey(), keys.subject()));
    }

    @Test
    void keepsThePairAcrossRestarts() {
        var first = open();
        assertTrue(Files.exists(dir.resolve("vapid.json")));
        var second = open();
        assertEquals(first.publicKey(), second.publicKey(), "a new pair would end every subscription");
        assertEquals(first.privateKey(), second.privateKey());
    }
}
