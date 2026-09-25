package in.virit.iot.simulation;

import in.virit.iot.led.LedService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedLedService extends LedService {

    private int pin = 26;
    private boolean on;

    @Override
    public synchronized State state() {
        return new State(pin, on, true);
    }

    @Override
    public synchronized void selectPin(int pin) {
        if (pin < 0 || pin > 27) throw new IllegalArgumentException("Use a BCM GPIO number between 0 and 27.");
        if (this.pin == pin) return;
        if (on) throw new IllegalStateException("Turn the LED off before changing GPIO.");
        this.pin = pin;
    }

    @Override
    public synchronized void setOn(int expectedPin, boolean on) {
        if (expectedPin != pin) throw new IllegalStateException("GPIO selection changed. Please try again.");
        this.on = on;
    }
}
