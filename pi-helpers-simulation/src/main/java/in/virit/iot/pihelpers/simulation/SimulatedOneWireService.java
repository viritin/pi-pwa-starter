package in.virit.iot.pihelpers.simulation;

import in.virit.iot.pihelpers.OneWireService;
import in.virit.iot.pihelpers.Simulated;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.List;

@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedOneWireService extends OneWireService implements Simulated {

    @Override
    public boolean isBusPresent() {
        return true;
    }

    @Override
    public List<Sensor> read() {
        double t = System.currentTimeMillis() / 60000.0;
        return List.of(
                new Sensor("28-000005e2fdc3", "DS18B20", round(21.4 + 1.5 * Math.sin(t)), null),
                new Sensor("28-0316a279b3ff", "DS18B20", round(4.1 + 0.6 * Math.cos(t / 3)), null));
    }

    @Override
    public int phantoms() {
        return 0;
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
