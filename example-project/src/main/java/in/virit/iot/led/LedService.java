package in.virit.iot.led;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** One shared LED output for the application, initialized on first use. */
@ApplicationScoped
public class LedService {
    @ConfigProperty(name = "starter.gpio.simulated", defaultValue = "false")
    boolean simulated;

    private Context context;
    private DigitalOutput output;
    private int pin = 17;
    private boolean on;

    public record State(int pin, boolean on, boolean simulated) {}

    public synchronized State state() {
        return new State(pin, on, simulated);
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
        if (!simulated) {
            if (output == null) {
                try {
                    context = Pi4J.newAutoContext();
                    output = context.create(DigitalOutput.newConfigBuilder(context)
                            .id("starter-led").name("Blink a LED")
                            .bcm(pin).provider("ffm-digital-output")
                            .initial(DigitalState.LOW).shutdown(DigitalState.LOW).build());
                } catch (RuntimeException failure) {
                    release();
                    throw failure;
                }
            }
            output.state(on ? DigitalState.HIGH : DigitalState.LOW);
        }
        // Publish the new value only after the write succeeds.
        this.on = on;
    }

    @PreDestroy
    synchronized void release() {
        if (context != null) {
            context.shutdown();
            context = null;
            output = null;
        }
        on = false;
    }
}
