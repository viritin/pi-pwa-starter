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

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/** Starts the packaged application and exercises real browser navigation. */
@QuarkusIntegrationTest
class ApplicationIT {

    @TestHTTPResource
    URI baseUri;

    @Test
    void applicationAndEveryViewOpen() {
        try (var playwright = Playwright.create();
             Browser browser = playwright.chromium().launch();
             BrowserContext context = browser.newContext(
                     new Browser.NewContextOptions().setViewportSize(1280, 900))) {
            Page page = context.newPage();
            page.setDefaultTimeout(15_000);
            page.navigate(baseUri.toString());

            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Small device. Big possibilities.").setExact(true)))
                    .isVisible();

            var about = page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("About").setExact(true));
            var system = page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("System").setExact(true));
            assertThat(about).isVisible();
            assertThat(system).isVisible();

            system.click();
            assertThat(page).hasURL(baseUri.resolve("system").toString());
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("System Monitor").setExact(true))).isVisible();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Run GC").setExact(true))).isVisible();

            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Reboot").setExact(true))).isEnabled();
            assertThat(page.getByRole(AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName("Shutdown").setExact(true))).isEnabled();

            var blink = page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName("Blink a LED").setExact(true));
            assertThat(blink).isVisible();
            blink.click();
            assertThat(page.locator(".page h1")).hasText("Blink a LED");
            var gpio = page.getByRole(AriaRole.SPINBUTTON,
                    new Page.GetByRoleOptions().setName("GPIO number (BCM)").setExact(true));
            var led = page.getByRole(AriaRole.SWITCH,
                    new Page.GetByRoleOptions().setName("LED on").setExact(true));
            gpio.fill("18");
            gpio.press("Tab");
            assertThat(page.locator("#led-status")).hasText("Simulation · GPIO 18 · LED off");
            led.click();
            assertThat(led).isChecked();
            assertThat(gpio).isDisabled();
            assertThat(page.locator("#led-status")).hasText("Simulation · GPIO 18 · LED on (HIGH)");
            led.click();
            assertThat(led).not().isChecked();
            assertThat(gpio).isEnabled();
            assertThat(page.locator("#led-status")).hasText("Simulation · GPIO 18 · LED off");

            about.click();
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Small device. Big possibilities.").setExact(true)))
                    .isVisible();

            // Direct URLs must work too, including the About alias.
            for (String route : new String[]{"about", "system", "blink-led"}) {
                page.navigate(baseUri.resolve(route).toString());
                assertThat(page.locator(".page h1")).hasText(route.equals("system")
                        ? "System Monitor" : route.equals("blink-led")
                        ? "Blink a LED" : "Small device. Big possibilities.");
            }
        }
    }
}
