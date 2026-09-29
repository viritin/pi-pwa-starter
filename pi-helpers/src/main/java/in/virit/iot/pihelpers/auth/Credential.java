package in.virit.iot.pihelpers.auth;

import io.quarkus.security.webauthn.WebAuthnCredentialRecord.RequiredPersistedData;

import java.time.Instant;
import java.util.UUID;

/**
 * One registered passkey. Mirrors the fields the WebAuthn extension needs to
 * persist ({@link RequiredPersistedData}) as plain, Jackson-friendly values, so
 * this record is what lands on disk while the extension's own types stay out of
 * our JSON. {@code publicKey} is written as Base64 by Jackson.
 */
public record Credential(
        String credentialId,
        byte[] publicKey,
        long publicKeyAlgorithm,
        long counter,
        UUID aaguid,
        String label,
        Instant createdAt) {

    /** The extension's persisted form; the username lives on the owning {@link User}. */
    public RequiredPersistedData toRequiredPersistedData(String username) {
        return new RequiredPersistedData(username, credentialId, aaguid, publicKey, publicKeyAlgorithm, counter);
    }

    public static Credential from(RequiredPersistedData data, String label, Instant createdAt) {
        return new Credential(data.credentialId(), data.publicKey(), data.publicKeyAlgorithm(),
                data.counter(), data.aaguid(), label, createdAt);
    }

    public Credential withCounter(long newCounter) {
        return new Credential(credentialId, publicKey, publicKeyAlgorithm, newCounter, aaguid, label, createdAt);
    }
}
