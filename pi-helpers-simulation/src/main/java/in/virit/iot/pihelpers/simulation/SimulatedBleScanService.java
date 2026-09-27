package in.virit.iot.pihelpers.simulation;

import in.virit.iot.pihelpers.BleScanService;
import com.vaadin.flow.shared.Registration;
import in.virit.iot.pihelpers.Simulated;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedBleScanService extends BleScanService implements Simulated {

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile List<Device> nearby = List.of();
    private volatile String status = "Not scanning";
    private boolean scanning;
    private int watchers;

    @Override
    public synchronized boolean isScanning() {
        return scanning;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public boolean hasProblem() {
        return false;
    }

    @Override
    public List<Device> devices() {
        var result = new ArrayList<>(nearby);
        result.sort(Comparator.comparing((Device device) -> device.rssi() == null ? Integer.MIN_VALUE : device.rssi())
                .reversed().thenComparing(Device::address));
        return result;
    }

    @Override
    public Registration addListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    @Override
    public synchronized void watch() {
        watchers++;
        start();
    }

    @Override
    public synchronized void unwatch() {
        watchers = Math.max(0, watchers - 1);
        if (watchers == 0) stop();
    }

    @Override
    public synchronized void start() {
        if (scanning) return;
        scanning = true;
        Instant now = Instant.now();
        nearby = List.of(
                new Device("C3:A1:5E:7B:22:9F", "Ruuvi 229F", -58, 4, false,
                        List.of("0x0499 Ruuvi Innovations"), List.of(), "05 12 FC 53 94 C3 7C 00 04 FF FC 04 0C AC 36 42", now),
                new Device("E0:5A:1B:67:42:C1", "Shelly BLU Button", -64, null, false,
                        List.of("0x0397 Shelly (Allterco)"), List.of("0000fcd2-0000-1000-8000-00805f9b34fb"), "97 03 01 2A", now),
                new Device("F4:12:FA:3C:81:D0", "Thingy", -71, null, false,
                        List.of("0x0059 Nordic Semiconductor"), List.of("ef680100-9b35-4933-9b10-52ffa9740042"), "59 00 01 02", now),
                new Device("58:2D:34:0A:11:7C", null, -80, null, false,
                        List.of("0x004C Apple"), List.of(), "4C 00 10 05 0B 1C 3F 8A 21", now),
                new Device("A4:C1:38:9E:20:55", "LYWSD03MMC", -88, null, false,
                        List.of("0x038F Xiaomi"), List.of("0000181a-0000-1000-8000-00805f9b34fb"), null, now));
        status = "Simulation · scanning · " + nearby.size() + " in range";
        fire();
    }

    @Override
    public synchronized void stop() {
        if (!scanning) return;
        scanning = false;
        status = "Not scanning";
        fire();
    }

    private void fire() {
        listeners.forEach(Runnable::run);
    }
}
