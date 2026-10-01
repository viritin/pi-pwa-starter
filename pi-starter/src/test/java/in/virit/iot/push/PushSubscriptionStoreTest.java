package in.virit.iot.push;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Subscriptions as files: they survive a restart, and a browser has one at a time. */
class PushSubscriptionStoreTest {

    @TempDir
    Path dir;
    PushSubscriptionStore store;

    @BeforeEach
    void freshStore() {
        store = open();
    }

    private PushSubscriptionStore open() {
        var s = new PushSubscriptionStore();
        s.dir = dir.toString();
        s.load();
        return s;
    }

    private static AlertSubscription subscription(String clientId, String endpoint) {
        return new AlertSubscription(clientId, endpoint, "p256dh", "auth", 18.0, 25.0, "ada", Instant.now());
    }

    @Test
    void survivesARestart() {
        store.save(subscription("phone-1", "https://push.example/1"));
        var reloaded = open().find("phone-1").orElseThrow();
        assertEquals("https://push.example/1", reloaded.endpoint());
        assertEquals(25.0, reloaded.max());
        assertEquals("ada", reloaded.username());
    }

    @Test
    void theSameEndpointUnderANewIdReplacesTheOld() {
        store.save(subscription("old-id", "https://push.example/1"));
        store.save(subscription("new-id", "https://push.example/1"));
        assertFalse(store.find("old-id").isPresent());
        assertEquals(1, open().all().size());
    }

    @Test
    void deletes() {
        store.save(subscription("phone-1", "https://push.example/1"));
        store.delete("phone-1");
        assertTrue(open().all().isEmpty());
    }

    @Test
    void rejectsIdsThatCouldNameOtherFiles() {
        assertThrows(IllegalArgumentException.class, () -> store.save(subscription("../evil", "https://x")));
        assertFalse(PushSubscriptionStore.isValidClientId(null));
        assertTrue(PushSubscriptionStore.isValidClientId("0f8e2c1a-1234-4c4c-9e9e-abcdef012345"));
    }
}
