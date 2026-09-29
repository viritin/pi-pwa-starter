package in.virit.iot.pihelpers.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The single switch for the optional passkey login. With
 * {@code starter.auth.enabled=false} (the default) nothing is enforced and the
 * application behaves exactly as it did before authentication existed.
 */
@ApplicationScoped
public class AuthConfig {

    @ConfigProperty(name = "starter.auth.enabled", defaultValue = "false")
    boolean enabled;

    @Inject
    UserStore users;

    public boolean authEnabled() {
        return enabled;
    }

    /**
     * True while sign-in is required but no passkey has been registered yet: the
     * one window in which the first administrator may enroll without an invite.
     */
    public boolean bootstrapMode() {
        return enabled && !users.hasAnyCredential();
    }
}
