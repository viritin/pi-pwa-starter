package in.virit.iot.simulation;

import in.virit.iot.pihelpers.GpioService;
import in.virit.iot.pihelpers.GpioInputSimulator;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import com.vaadin.flow.shared.Registration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedGpioService extends GpioService implements GpioInputSimulator {

    private record Entry(Mode mode, Pull pull, boolean high) {}

    private final Map<Integer, Entry> entries = new TreeMap<>();
    private final List<Consumer<Pin>> listeners = new CopyOnWriteArrayList<>();

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public synchronized List<Pin> pins() {
        var pins = new ArrayList<Pin>(MAX_BCM + 1);
        for (int bcm = 0; bcm <= MAX_BCM; bcm++) pins.add(pin(bcm));
        return pins;
    }

    @Override
    public synchronized Pin pin(int bcm) {
        var entry = entries.get(bcm);
        return entry == null ? new Pin(bcm, Mode.UNUSED, Pull.OFF, false, false)
                : new Pin(bcm, entry.mode(), entry.pull(), entry.high(), false);
    }

    @Override
    public synchronized void configure(int bcm, Mode mode, Pull pull) {
        if (bcm < 0 || bcm > MAX_BCM) throw new IllegalArgumentException("BCM GPIO must be 0–27.");
        var current = entries.get(bcm);
        if (current != null && current.mode() == mode && (mode != Mode.INPUT || current.pull() == pull)) return;
        if (mode == Mode.UNUSED) entries.remove(bcm);
        else entries.put(bcm, new Entry(mode, mode == Mode.INPUT ? pull : Pull.OFF,
                mode == Mode.INPUT && pull == Pull.UP));
        fire(pin(bcm));
    }

    @Override
    public synchronized void write(int bcm, boolean high) {
        var entry = entries.get(bcm);
        if (entry == null || entry.mode() != Mode.OUTPUT) {
            throw new IllegalStateException("GPIO " + bcm + " is not configured as an output.");
        }
        entries.put(bcm, new Entry(entry.mode(), entry.pull(), high));
        fire(pin(bcm));
    }

    @Override
    public synchronized void simulateInput(int bcm, boolean high) {
        var entry = entries.get(bcm);
        if (entry == null || entry.mode() != Mode.INPUT) {
            throw new IllegalStateException("GPIO " + bcm + " is not configured as an input.");
        }
        entries.put(bcm, new Entry(entry.mode(), entry.pull(), high));
        fire(pin(bcm));
    }

    @Override
    public synchronized void releaseAll() {
        for (int bcm : List.copyOf(entries.keySet())) {
            entries.remove(bcm);
            fire(pin(bcm));
        }
    }

    @Override
    public Registration addListener(Consumer<Pin> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    private void fire(Pin pin) {
        for (var listener : listeners) listener.accept(pin);
    }
}
