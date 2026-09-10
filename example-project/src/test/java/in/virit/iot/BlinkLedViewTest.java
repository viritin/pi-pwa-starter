package in.virit.iot;

import com.vaadin.flow.component.checkbox.Switch;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The LED example, driven like a user: pick a GPIO, switch it on and off. */
@QuarkusTest
class BlinkLedViewTest extends ViewTest {

    @Test
    void selectsAPinAndTogglesTheLed() {
        navigate(BlinkLedView.class);
        var gpio = findIntegerField().withLabel("GPIO number (BCM)");
        var led = find(Switch.class).withLabel("LED on").first();

        // Same state as before the test may be left by another test class: turn it off first.
        if (Boolean.TRUE.equals(led.getValue())) {
            toggle(led);
        }
        test(gpio.component()).setValue(18);
        assertEquals("Simulation · GPIO 18 · LED off", paragraph("led-status"));

        toggle(led);
        assertEquals("Simulation · GPIO 18 · LED on (HIGH)", paragraph("led-status"));
        assertFalse(gpio.component().isEnabled(), "the pin cannot change while the LED is on");

        toggle(led);
        assertEquals("Simulation · GPIO 18 · LED off", paragraph("led-status"));
        assertTrue(gpio.component().isEnabled());
    }
}
