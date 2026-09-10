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

            // Climate: the simulated BME280 has history from the start, so the curve is there at once.
            page.navigate(baseUri.resolve("bme280").toString());
            assertThat(page.locator(".page h1")).hasText("Climate");
            assertThat(page.locator("#bme280-status")).hasText("Simulation · BME280");
            assertThat(page.locator(".climate-card").first()).containsText("Humidity");
            assertThat(page.locator(".climate-card svg").first()).isVisible();
            page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("24 h").setExact(true)).click();
            assertThat(page.locator(".climate-card svg").first()).isVisible();

            // The prototyping screens sit under one navigation group.
            assertThat(page.getByText("Proto Tools").first()).isVisible();

            // GPIO: make header pin 11 (GPIO17) an output and drive it high.
            page.navigate(baseUri.resolve("gpio").toString());
            assertThat(page.locator(".page h1")).hasText("GPIO");
            assertThat(page.locator("#gpio-status")).hasText("Simulation · 0 pins configured");
            page.locator("#pin-11").click();
            page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("Output").setExact(true)).click();
            assertThat(page.locator("#pin-level")).hasText("Level: LOW");
            page.getByRole(AriaRole.SWITCH,
                    new Page.GetByRoleOptions().setName("Drive HIGH (3.3 V)").setExact(true)).click();
            assertThat(page.locator("#pin-level")).hasText("Level: HIGH");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Close").setExact(true)).click();
            assertThat(page.locator("#pin-11 .pin-state")).hasText("OUT · H");
            assertThat(page.locator("#gpio-status")).hasText("Simulation · 1 pin configured");

            // I2C: the simulated bus answers at 0x76 with a BME280 chip id in register 0xD0.
            page.navigate(baseUri.resolve("i2c").toString());
            assertThat(page.locator(".page h1")).hasText("I²C");
            page.locator("#i2c-scan").click();
            assertThat(page.locator("#i2c-status")).hasText("Simulation · 4 devices on i2c-1");
            page.locator("#i2c-0x76").click();
            page.getByRole(AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName("Start register (hex)").setExact(true)).fill("D0");
            page.locator("#i2c-read").click();
            assertThat(page.locator("#i2c-dump")).containsText("D0: 60");

            // PWM: the simulated chip accepts a servo pulse.
            page.navigate(baseUri.resolve("pwm").toString());
            assertThat(page.locator(".page h1")).hasText("PWM & servo");
            page.getByRole(AriaRole.SWITCH,
                    new Page.GetByRoleOptions().setName("Output enabled").setExact(true)).click();
            assertThat(page.locator("#pwm-status")).hasText("Simulation · pwmchip0/pwm0 · on · 50 Hz · 1500 µs high (7.5 %)");
            page.locator("#servo-0").click();
            assertThat(page.locator("#pwm-status")).hasText("Simulation · pwmchip0/pwm0 · on · 50 Hz · 500 µs high (2.5 %)");

            // 1-Wire: two simulated probes report temperatures.
            page.navigate(baseUri.resolve("onewire").toString());
            assertThat(page.locator(".page h1")).hasText("1-Wire sensors");
            assertThat(page.locator("#onewire-status")).containsText("Simulation · 2 devices");
            assertThat(page.locator(".onewire-panel .stat-value").first()).containsText("°C");

            // The System screen now also reports host interfaces.
            page.navigate(baseUri.resolve("system").toString());
            assertThat(page.getByRole(AriaRole.HEADING,
                    new Page.GetByRoleOptions().setName("Interfaces").setExact(true))).isVisible();

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
