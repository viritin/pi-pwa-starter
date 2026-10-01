package in.virit.iot.push;

import in.virit.iot.pihelpers.Json;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Optional;

/**
 * The VAPID key pair that signs this server's pushes. Configured keys win;
 * otherwise a pair is generated on first start and kept in {@code vapid.json}
 * under {@code starter.push.dir}. It must stay the same: browsers bind their
 * subscriptions to the public key, so a new pair silently ends every one of them.
 */
@ApplicationScoped
public class VapidKeys {

    private static final Logger LOG = Logger.getLogger(VapidKeys.class);

    @ConfigProperty(name = "starter.push.dir", defaultValue = "${user.home}/.pipwa/push")
    String dir;

    @ConfigProperty(name = "starter.push.vapid-public-key")
    Optional<String> configuredPublicKey;

    @ConfigProperty(name = "starter.push.vapid-private-key")
    Optional<String> configuredPrivateKey;

    /** A contact the push services can reach; Apple's rejects pushes without one. */
    @ConfigProperty(name = "starter.push.subject", defaultValue = "mailto:admin@example.com")
    String subject;

    private Pair pair;

    /** The pair as base64url without padding: the 65-byte uncompressed point and the 32-byte scalar. */
    record Pair(String publicKey, String privateKey) {
    }

    @PostConstruct
    void load() {
        if (configuredPublicKey.isPresent() && configuredPrivateKey.isPresent()) {
            pair = new Pair(configuredPublicKey.get(), configuredPrivateKey.get());
            return;
        }
        Path file = Path.of(dir).resolve("vapid.json");
        try {
            if (Files.exists(file)) {
                pair = Json.read(Files.readString(file), Pair.class);
                return;
            }
            pair = generate();
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(pair));
            ownerOnly(file);
            LOG.infof("Generated a VAPID key pair for Web Push in %s", file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read or write " + file + ": " + e.getMessage(), e);
        }
    }

    public String publicKey() {
        return pair.publicKey();
    }

    public String privateKey() {
        return pair.privateKey();
    }

    public String subject() {
        return subject;
    }

    static Pair generate() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var keys = generator.generateKeyPair();
            var point = ((ECPublicKey) keys.getPublic()).getW();
            byte[] uncompressed = new byte[65];
            uncompressed[0] = 0x04;
            System.arraycopy(fixed32(point.getAffineX()), 0, uncompressed, 1, 32);
            System.arraycopy(fixed32(point.getAffineY()), 0, uncompressed, 33, 32);
            byte[] scalar = fixed32(((ECPrivateKey) keys.getPrivate()).getS());
            var base64 = Base64.getUrlEncoder().withoutPadding();
            return new Pair(base64.encodeToString(uncompressed), base64.encodeToString(scalar));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("This JVM cannot generate P-256 keys", e);
        }
    }

    /** A BigInteger as exactly 32 bytes: without its sign byte, left-padded with zeros. */
    private static byte[] fixed32(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        int copy = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - copy, result, 32 - copy, copy);
        return result;
    }

    private static void ownerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            LOG.debugf("Could not restrict permissions of %s", file);
        }
    }
}
