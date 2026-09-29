package in.virit.iot.pihelpers.auth;

import java.time.Instant;
import java.util.Set;

/**
 * A single-use, expiring registration link. The admin creates one per new user;
 * the invitee opens {@code /register?token=...} and enrolls their own passkey.
 * The username is fixed here, server-side, so the browser can never register a
 * credential for someone else.
 */
public record InviteToken(String token, String username, String displayName, Set<String> roles,
                          Instant expiry, boolean used) {

    public InviteToken {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean valid() {
        return !used && Instant.now().isBefore(expiry);
    }

    public InviteToken consumed() {
        return new InviteToken(token, username, displayName, roles, expiry, true);
    }
}
