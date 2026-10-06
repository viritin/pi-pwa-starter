package in.virit.iot;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one test that needs a browser: does the packaged application reach a
 * real browser as a working PWA? Everything about what the views do is covered
 * without a browser in the browserless tests; this only checks that the server
 * delivers the frontend bundle, the PWA resources and a live push connection,
 * and that every route renders when opened directly in a fresh browser.
 */
@QuarkusIntegrationTest
class PwaSmokeIT {

    /** The view's own heading: MobileMainLayout puts the view next to its header row in .mobile-content. */
    private static final String VIEW_HEADING = ".mobile-content > :not(.mobile-content-header) h1";

    private static final String[] ROUTES = {"about", "system", "blinkled", "climate", "gpio", "i2c", "pwm", "onewire", "ble"};

    @TestHTTPResource
    URI baseUri;

    @Test
    void theBrowserRendersThePwa() {
        try (var playwright = Playwright.create();
             Browser browser = playwright.chromium().launch()) {
            Page page = browser.newContext().newPage();
            page.setDefaultTimeout(15_000);

            // PWA resources the browser needs to install the app
            assertEquals(200, page.request().get(baseUri.resolve("manifest.webmanifest").toString()).status());
            assertEquals(200, page.request().get(baseUri.resolve("sw.js").toString()).status());

            // The front page renders and client-side navigation works over the live connection
            page.navigate(baseUri.toString());
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Small device. Big possibilities.").setExact(true))).isVisible();
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("System").setExact(true)).click();
            assertThat(page).hasURL(baseUri.resolve("system").toString());
            assertThat(page.locator(VIEW_HEADING)).hasText("System Monitor");

            // Every route in a fresh browser: a reused production bundle can miss a new
            // route's chunk, which only shows when nothing else has loaded its components.
            for (String route : ROUTES) {
                try (BrowserContext fresh = browser.newContext()) {
                    Page direct = fresh.newPage();
                    var browserErrors = new ArrayList<String>();
                    direct.onPageError(error -> browserErrors.add("pageerror: " + error));
                    direct.onConsoleMessage(message -> {
                        if (message.type().equals("error")) browserErrors.add("console: " + message.text());
                    });
                    direct.onRequestFailed(request -> browserErrors.add("request failed: "
                            + request.url() + " (" + request.failure() + ")"));
                    direct.setDefaultTimeout(15_000);
                    direct.navigate(baseUri.resolve(route).toString());
                    assertThat(direct.locator(VIEW_HEADING)).isVisible();
                    direct.waitForTimeout(500);
                    Object undefined = direct.evaluate("() => [...new Set([...document.querySelectorAll('*')]"
                            + ".map(e => e.tagName.toLowerCase()).filter(t => t.startsWith('vaadin-') && !customElements.get(t)))].join(',')");
                    assertEquals("", undefined, "Undefined Vaadin elements on /" + route + " in a fresh browser. "
                            + "Browser errors: " + browserErrors);
                }
            }
        }
    }
}
