package in.virit.iot.pihelpers.auth;

import io.quarkus.security.webauthn.WebAuthnCredentialRecord;
import io.quarkus.security.webauthn.WebAuthnUserProvider;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Bridges the Quarkus WebAuthn extension to our file-backed {@link UserStore}.
 * The extension calls these to look up, store and count-update credentials, and
 * to learn a user's roles when it builds the {@code SecurityIdentity}.
 * <p>
 * {@code @Blocking} tells {@code WebAuthnAuthenticatorStorage} to run the
 * {@link Uni}-returning methods on a worker thread, so the file writes never
 * block the event loop. {@link #getRoles} is called synchronously by the
 * identity provider, so it is served straight from the in-memory cache.
 */
@ApplicationScoped
@Blocking
public class FileWebAuthnUserProvider implements WebAuthnUserProvider {

    @Inject
    UserStore users;

    @Override
    public Uni<List<WebAuthnCredentialRecord>> findByUsername(String username) {
        return Uni.createFrom().item(() -> users.find(username)
                .map(u -> u.credentials().stream()
                        .map(c -> WebAuthnCredentialRecord.fromRequiredPersistedData(c.toRequiredPersistedData(username)))
                        .toList())
                .orElseGet(List::of));
    }

    @Override
    public Uni<WebAuthnCredentialRecord> findByCredentialId(String credentialId) {
        return Uni.createFrom().item(() -> users.findByCredentialId(credentialId)
                .map(l -> WebAuthnCredentialRecord.fromRequiredPersistedData(
                        l.credential().toRequiredPersistedData(l.username())))
                .orElse(null));
    }

    @Override
    public Uni<Void> store(WebAuthnCredentialRecord credentialRecord) {
        return Uni.createFrom().item(() -> {
            var data = credentialRecord.getRequiredPersistedData();
            users.addCredential(data.username(), Credential.from(data, "Passkey", Instant.now()));
            return (Void) null;
        });
    }

    @Override
    public Uni<Void> update(String credentialId, long counter) {
        return Uni.createFrom().item(() -> {
            users.updateCounter(credentialId, counter);
            return (Void) null;
        });
    }

    @Override
    public Set<String> getRoles(String username) {
        return users.rolesFor(username);
    }
}
