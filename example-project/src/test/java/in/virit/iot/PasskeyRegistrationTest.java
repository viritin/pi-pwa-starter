package in.virit.iot;

import in.virit.iot.pihelpers.auth.PasskeyRoutes;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the custom, invite/bootstrap-gated registration endpoint end to end
 * on the server: route wiring, the WebAuthn challenge and its cookie. The browser
 * half of the ceremony ({@code navigator.credentials}) is out of scope here.
 */
@QuarkusTest
@TestProfile(PasskeyRegistrationTest.BootstrapProfile.class)
class PasskeyRegistrationTest {

    @TestHTTPResource
    URI root;

    @Test
    void bootstrapReturnsARegistrationChallenge() throws Exception {
        HttpResponse<String> response = get(root.resolve(
                PasskeyRoutes.OPTIONS_PATH + "?username=admin&displayName=Admin"));
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("challenge"), () -> "expected a challenge, got: " + response.body());
        assertTrue(response.headers().firstValue("set-cookie").isPresent(), "challenge cookie must be set");
    }

    @Test
    void registrationWithoutInviteOrBootstrapIsRejected() throws Exception {
        // A token that does not exist: neither a valid invite nor (with the admin
        // request above) necessarily bootstrap — the point is a bad token fails.
        HttpResponse<String> response = get(root.resolve(PasskeyRoutes.OPTIONS_PATH + "/does-not-exist"));
        assertEquals(400, response.statusCode());
    }

    private static HttpResponse<String> get(URI uri) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    public static class BootstrapProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "starter.auth.enabled", "true",
                    "starter.users-dir", "target/test-users-bootstrap");
        }
    }
}
