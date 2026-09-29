package in.virit.iot;

import com.vaadin.flow.component.UI;
import in.virit.iot.pihelpers.auth.AuthConfig;
import in.virit.iot.pihelpers.auth.LoginView;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** With sign-in enabled but nobody authenticated, protected routes must divert to the login page. */
@QuarkusTest
@TestProfile(AuthGateTest.AuthEnabled.class)
class AuthGateTest extends ViewTest {

    @Override
    protected Set<String> scanPackages() {
        return Set.of(TopLayout.class.getPackageName(), LoginView.class.getPackageName());
    }

    @Inject
    AuthConfig authConfig;

    @Test
    void configOverrideApplies() {
        assertTrue(authConfig.authEnabled(), "test profile should enable auth");
    }

    @Test
    void protectedRouteRedirectsToLogin() {
        // Navigate through the Flow API so the reroute is observed rather than asserted away.
        UI.getCurrent().navigate(SystemView.class);
        assertInstanceOf(LoginView.class, getCurrentView());
    }

    @Test
    void aboutStaysPublic() {
        navigate(AboutView.class);
        assertInstanceOf(AboutView.class, getCurrentView());
    }

    @Test
    void loginPageIsReachable() {
        navigate(LoginView.class);
        assertInstanceOf(LoginView.class, getCurrentView());
    }

    public static class AuthEnabled implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("starter.auth.enabled", "true");
        }
    }
}
