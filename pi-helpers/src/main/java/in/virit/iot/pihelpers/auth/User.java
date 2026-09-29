package in.virit.iot.pihelpers.auth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An application user, stored as one JSON file. Passwordless: identity is proven
 * by the registered {@link Credential}s (passkeys). {@code roles} feed Quarkus's
 * {@code SecurityIdentity} through {@link FileWebAuthnUserProvider#getRoles}.
 */
public record User(String username, String displayName, Set<String> roles, List<Credential> credentials) {

    public User {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        credentials = credentials == null ? List.of() : List.copyOf(credentials);
    }

    public static User of(String username, String displayName, Set<String> roles) {
        return new User(username, displayName, new LinkedHashSet<>(roles), List.of());
    }

    public boolean isAdmin() {
        return roles.contains("admin");
    }

    public User withAdded(Credential credential) {
        var next = new ArrayList<>(credentials);
        next.add(credential);
        return new User(username, displayName, roles, next);
    }

    public User withProfile(String displayName, Set<String> roles) {
        return new User(username, displayName, roles, credentials);
    }
}
