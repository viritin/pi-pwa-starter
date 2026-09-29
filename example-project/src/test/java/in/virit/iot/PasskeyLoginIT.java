package in.virit.iot;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.CDPSession;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The passkey ceremony from a real browser, driven by Chromium's virtual
 * authenticator (CDP) — proof that the whole flow works on {@code localhost},
 * which browsers treat as a secure context. Covers first-admin bootstrap through
 * the top-bar button, admin-only access, sign-out, and usernameless sign-in with
 * the resident credential. Runs in the {@code verify} phase against the packaged
 * app, like {@link PwaSmokeIT}.
 */
@QuarkusIntegrationTest
@TestProfile(PasskeyLoginIT.AuthOnFreshStore.class)
class PasskeyLoginIT {

    /** The view's own heading inside MobileMainLayout's content area. */
    private static final String VIEW_HEADING = ".mobile-content > :not(.mobile-content-header) h1";
    private static final String ABOUT_HEADING = "Small device. Big possibilities.";

    /** A unique data dir per run so the store always starts empty (bootstrap mode). */
    private static final String IT_USERS_DIR = "target/it-users-" + System.currentTimeMillis();

    @TestHTTPResource
    URI baseUri;

    @Test
    void bootstrapAdminThenPasskeySignIn() {
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            page.setDefaultTimeout(20_000);
            addVirtualAuthenticator(context, page);

            // Land on the public About page; the menu offers first-run setup.
            page.navigate(baseUri.toString());
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();

            // Bootstrap: create the first administrator's passkey.
            page.locator("#first-admin-username input").fill("admin");
            page.locator("#first-admin-create").click();
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(ABOUT_HEADING).setExact(true))).isVisible();

            // Now authenticated as an admin: the admin-only Users view opens.
            page.navigate(baseUri.resolve("users").toString());
            assertThat(page.locator(VIEW_HEADING)).hasText("Users");

            // Sign out via the menu; bootstrap is over.
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sign out").setExact(true)).click();
            assertThat(page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Sign in").setExact(true))).isVisible();

            // Usernameless sign-in with the resident credential just registered.
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sign in").setExact(true)).click();
            page.locator("#sign-in-passkey").click();
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName(ABOUT_HEADING).setExact(true))).isVisible();
            page.navigate(baseUri.resolve("system").toString());
            assertThat(page.locator(VIEW_HEADING)).hasText("System Monitor");
        }
    }

    private static void addVirtualAuthenticator(BrowserContext context, Page page) {
        CDPSession cdp = context.newCDPSession(page);
        cdp.send("WebAuthn.enable");
        JsonObject options = new JsonObject();
        options.addProperty("protocol", "ctap2");
        options.addProperty("transport", "internal");
        options.addProperty("hasResidentKey", true);
        options.addProperty("hasUserVerification", true);
        options.addProperty("isUserVerified", true);
        options.addProperty("automaticPresenceSimulation", true);
        JsonObject params = new JsonObject();
        params.add("options", options);
        cdp.send("WebAuthn.addVirtualAuthenticator", params);
    }

    public static class AuthOnFreshStore implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "starter.auth.enabled", "true",
                    "starter.users-dir", IT_USERS_DIR);
        }
    }
}
