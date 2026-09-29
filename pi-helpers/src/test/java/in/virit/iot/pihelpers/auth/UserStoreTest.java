package in.virit.iot.pihelpers.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The file-backed store: persistence fidelity, the invite lifecycle and the bootstrap flag. */
class UserStoreTest {

    UserStore store;

    @BeforeEach
    void freshStore(@TempDir Path dir) {
        store = new UserStore();
        store.usersDir = dir.toString();
        store.load();
    }

    private static Credential credential(String id) {
        return new Credential(id, new byte[]{1, 2, 3, 4}, -7L, 0L,
                UUID.fromString("00000000-0000-0000-0000-000000000000"), "Passkey", Instant.now());
    }

    @Test
    void userAndCredentialSurviveAReload(@TempDir Path dir) {
        store.ensureUser("ada", "Ada Lovelace", Set.of("admin", "user"));
        store.addCredential("ada", credential("cred-1"));

        UserStore reloaded = new UserStore();
        reloaded.usersDir = store.root().toString();
        reloaded.load();

        User ada = reloaded.find("ada").orElseThrow();
        assertEquals("Ada Lovelace", ada.displayName());
        assertEquals(Set.of("admin", "user"), ada.roles());
        assertEquals(1, ada.credentials().size());
        assertArrayEquals(new byte[]{1, 2, 3, 4}, ada.credentials().get(0).publicKey());
        assertEquals(-7L, ada.credentials().get(0).publicKeyAlgorithm());
        assertEquals("ada", reloaded.findByCredentialId("cred-1").orElseThrow().username());
        assertEquals(Set.of("admin", "user"), reloaded.rolesFor("ada"));
    }

    @Test
    void counterUpdatePersists() {
        store.ensureUser("ada", "Ada", Set.of("user"));
        store.addCredential("ada", credential("cred-1"));
        store.updateCounter("cred-1", 42L);
        assertEquals(42L, store.findByCredentialId("cred-1").orElseThrow().credential().counter());
    }

    @Test
    void hasAnyCredentialDrivesBootstrap() {
        assertFalse(store.hasAnyCredential());
        store.ensureUser("ada", "Ada", Set.of("admin"));
        assertFalse(store.hasAnyCredential(), "a user without a passkey is not yet enrolled");
        store.addCredential("ada", credential("cred-1"));
        assertTrue(store.hasAnyCredential());
    }

    @Test
    void inviteIsSingleUseAndExpires() {
        var invite = store.createInvite("bob", "Bob", Set.of("user"), Duration.ofDays(1));
        assertTrue(store.findInvite(invite.token()).orElseThrow().valid());

        store.consumeInvite(invite.token());
        assertFalse(store.findInvite(invite.token()).orElseThrow().valid(), "used invite is no longer valid");

        var expired = store.createInvite("eve", "Eve", Set.of("user"), Duration.ofSeconds(-1));
        assertFalse(store.findInvite(expired.token()).orElseThrow().valid(), "expired invite is not valid");
    }

    @Test
    void deleteRemovesUserAndCredentialIndex() {
        store.ensureUser("ada", "Ada", Set.of("user"));
        store.addCredential("ada", credential("cred-1"));
        store.delete("ada");
        assertTrue(store.find("ada").isEmpty());
        assertTrue(store.findByCredentialId("cred-1").isEmpty());
        assertFalse(store.hasAnyCredential());
    }
}
