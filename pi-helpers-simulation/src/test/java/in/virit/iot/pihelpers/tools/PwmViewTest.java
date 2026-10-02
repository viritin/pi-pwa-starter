package in.virit.iot.pihelpers.tools;

import in.virit.iot.pihelpers.testing.ViewTest;

import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Servo pulses and duty cycles on the simulated PWM chip. */
@QuarkusTest
class PwmViewTest extends ViewTest {

    @Test
    void drivesAServoAndADutyCycle() {
        navigate(PwmView.class);
        var enabled = find(Switch.class).withLabel("Output enabled").first();
        // The channels are read in the background and arrive over push
        awaitPush(enabled::isEnabled, "the PWM channels");
        if (Boolean.TRUE.equals(enabled.getValue())) {
            toggle(enabled);
        }
        assertTrue(paragraph("pwm-status").contains("off"));

        toggle(enabled);
        assertEquals("Simulation · pwmchip0/pwm0 · on · 50 Hz · 1500 µs high (7.5 %)", paragraph("pwm-status"));
        findButton().withText("0°").click();
        assertEquals("Simulation · pwmchip0/pwm0 · on · 50 Hz · 500 µs high (2.5 %)", paragraph("pwm-status"));

        test(find(RadioButtonGroup.class).withLabel("Signal").first()).selectItem("Duty cycle");
        assertEquals("Simulation · pwmchip0/pwm0 · on · 1000 Hz · 500 µs high (50.0 %)", paragraph("pwm-status"));

        toggle(enabled);
        assertTrue(paragraph("pwm-status").contains(" · off · "));
    }
}
