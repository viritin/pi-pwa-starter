package in.virit.iot.led;

import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import in.virit.iot.pihelpers.Pi4JContext;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * One shared LED output for the application, initialized on first use.
 * Uses the Pi4J context shared with the pi-helpers panels, so the GPIO screen
 * shows the LED pin as taken instead of fighting over it.
 */
@ApplicationScoped
public class LedService {

    @Inject
    Pi4JContext pi4j;

    private DigitalOutput output;
    private int pin = 26;
    private boolean on;

    public record State(int pin, boolean on) {}

    public synchronized State state() {
        return new State(pin, on);
    }

    public synchronized void selectPin(int pin) {
        if (pin < 0 || pin > 27) {
            throw new IllegalArgumentException("Use a BCM GPIO number between 0 and 27.");
        }
        if (this.pin == pin) {
            return;
        }
        if (on) {
            throw new IllegalStateException("Turn the LED off before changing GPIO.");
        }
        release();
        this.pin = pin;
    }

    public synchronized void setOn(int expectedPin, boolean on) {
        // Another browser may have changed the selected GPIO since this view refreshed.
        if (expectedPin != pin) {
            throw new IllegalStateException("GPIO selection changed. Please try again.");
        }
        if (output == null) {
            if (pi4j.isGpioInUse(pin)) {
                throw new IllegalStateException("GPIO " + pin + " is in use elsewhere, e.g. on the GPIO screen.");
            }
            var context = pi4j.context();
            output = context.create(DigitalOutput.newConfigBuilder(context)
                    .id("starter-led").name("Blink a LED")
                    .bcm(pin).provider("ffm-digital-output")
                    .initial(DigitalState.LOW).shutdown(DigitalState.LOW).build());
        }
        output.state(on ? DigitalState.HIGH : DigitalState.LOW);
        // Publish the new value only after the write succeeds.
        this.on = on;
    }

    @PreDestroy
    synchronized void release() {
        pi4j.release(output);
        output = null;
        on = false;
    }
}
