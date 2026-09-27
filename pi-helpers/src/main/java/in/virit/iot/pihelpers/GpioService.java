package in.virit.iot.pihelpers;

import com.pi4j.io.IO;
import com.pi4j.io.gpio.digital.DigitalInput;
import com.pi4j.io.gpio.digital.DigitalOutput;
import com.pi4j.io.gpio.digital.DigitalState;
import com.pi4j.io.gpio.digital.PullResistance;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Generic access to the 28 user GPIOs for prototyping: configure a pin as an
 * input (with pull resistor) or an output, read and drive it, and get notified
 * of input changes. Pins configured here are released when the application
 * shuts down. Pins held by other services through {@link Pi4JContext} are
 * reported as external and left alone.
 */
@ApplicationScoped
public class GpioService {

    private static final Logger LOG = Logger.getLogger(GpioService.class);
    public static final int MAX_BCM = 27;

    public enum Mode { UNUSED, INPUT, OUTPUT }

    public enum Pull { OFF, UP, DOWN }

    /**
     * @param external the pin is in use by another service of this application
     */
    public record Pin(int bcm, Mode mode, Pull pull, boolean high, boolean external) {
        public boolean configured() {
            return mode != Mode.UNUSED;
        }
    }

    private static final class Entry {
        Mode mode;
        Pull pull;
        volatile boolean high;
        IO io;
    }

    @Inject
    Pi4JContext pi4j;

    private final Map<Integer, Entry> entries = new TreeMap<>();
    private final List<Consumer<Pin>> listeners = new CopyOnWriteArrayList<>();


    public synchronized List<Pin> pins() {
        var pins = new ArrayList<Pin>(MAX_BCM + 1);
        for (int bcm = 0; bcm <= MAX_BCM; bcm++) {
            pins.add(pin(bcm));
        }
        return pins;
    }

    public synchronized Pin pin(int bcm) {
        var entry = entries.get(bcm);
        if (entry != null) {
            return new Pin(bcm, entry.mode, entry.pull, entry.high, false);
        }
        return new Pin(bcm, Mode.UNUSED, Pull.OFF, false, pi4j.isGpioInUse(bcm));
    }

    /** Configures the pin; UNUSED releases it. Outputs start LOW. */
    public synchronized void configure(int bcm, Mode mode, Pull pull) {
        checkBcm(bcm);
        var current = entries.get(bcm);
        if (current != null && current.mode == mode && (mode != Mode.INPUT || current.pull == pull)) {
            return;
        }
        if (current == null && pi4j.isGpioInUse(bcm)) {
            throw new IllegalStateException("GPIO " + bcm + " is in use by another part of the application.");
        }
        release(bcm);
        if (mode == Mode.UNUSED) {
            fire(pin(bcm));
            return;
        }
        var entry = new Entry();
        entry.mode = mode;
        entry.pull = mode == Mode.INPUT ? pull : Pull.OFF;
        if (mode == Mode.INPUT) {
            var context = pi4j.context();
            var input = context.create(DigitalInput.newConfigBuilder(context)
                    .id(id(bcm)).name("GPIO " + bcm).bcm(bcm)
                    .pull(switch (pull) {
                        case UP -> PullResistance.PULL_UP;
                        case DOWN -> PullResistance.PULL_DOWN;
                        case OFF -> PullResistance.OFF;
                    })
                    .debounce(5L)
                    .provider("ffm-digital-input").build());
            input.addListener(event -> onInputChange(bcm, event.state().isHigh()));
            entry.high = input.isHigh();
            entry.io = input;
        } else {
            var context = pi4j.context();
            entry.io = context.create(DigitalOutput.newConfigBuilder(context)
                    .id(id(bcm)).name("GPIO " + bcm).bcm(bcm)
                    .initial(DigitalState.LOW).shutdown(DigitalState.LOW)
                    .provider("ffm-digital-output").build());
        }
        entries.put(bcm, entry);
        LOG.infof("GPIO %d configured as %s%s", bcm, mode, mode == Mode.INPUT ? " pull " + pull : "");
        fire(pin(bcm));
    }

    /** Drives an output pin. */
    public synchronized void write(int bcm, boolean high) {
        var entry = entries.get(bcm);
        if (entry == null || entry.mode != Mode.OUTPUT) {
            throw new IllegalStateException("GPIO " + bcm + " is not configured as an output.");
        }
        if (entry.io != null) {
            ((DigitalOutput) entry.io).state(high ? DigitalState.HIGH : DigitalState.LOW);
        }
        entry.high = high;
        fire(pin(bcm));
    }

    public synchronized void releaseAll() {
        for (int bcm : List.copyOf(entries.keySet())) {
            release(bcm);
            fire(pin(bcm));
        }
    }

    /** Listener is called from Pi4J or UI threads; wrap UI updates in ui.access. */
    public Registration addListener(Consumer<Pin> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void onInputChange(int bcm, boolean high) {
        Entry entry;
        synchronized (this) {
            entry = entries.get(bcm);
            if (entry == null) {
                return;
            }
            entry.high = high;
        }
        fire(new Pin(bcm, entry.mode, entry.pull, high, false));
    }

    private void release(int bcm) {
        var entry = entries.remove(bcm);
        if (entry != null && entry.io != null) {
            pi4j.release(entry.io);
        }
    }

    private void fire(Pin pin) {
        for (var listener : listeners) {
            try {
                listener.accept(pin);
            } catch (RuntimeException e) {
                LOG.debug("GPIO listener failed", e);
            }
        }
    }

    private static void checkBcm(int bcm) {
        if (bcm < 0 || bcm > MAX_BCM) {
            throw new IllegalArgumentException("Use a BCM GPIO number between 0 and " + MAX_BCM + ".");
        }
    }

    private static String id(int bcm) {
        return "pi-helpers-gpio-" + bcm;
    }

    @PreDestroy
    synchronized void shutdown() {
        for (int bcm : List.copyOf(entries.keySet())) {
            release(bcm);
        }
    }
}
