package in.virit.iot.pihelpers;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.freedesktop.dbus.types.Variant;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lists the Bluetooth Low Energy devices advertising nearby, through BlueZ over
 * the system D-Bus. Nothing is connected to: the scanner only listens, which is
 * enough to see whether a tag, a phone or a sensor is in range and what it
 * announces. Scanning runs while a panel is watching and stops when the last
 * one leaves, so an idle application does not keep the radio busy.
 * <p>
 * BlueZ keeps a device object per address and updates its RSSI while the device
 * advertises; the RSSI disappears when it falls silent. That is what tells a
 * device in range from one BlueZ merely remembers.
 * <p>
 * Simulated mode invents a handful of typical devices.
 */
@ApplicationScoped
public class BleScanService {

    private static final Logger LOG = Logger.getLogger(BleScanService.class);
    static final Duration POLL = Duration.ofSeconds(2);
    /** A device not heard for this long drops off the list. */
    static final Duration FORGET_AFTER = Duration.ofSeconds(60);

    private static final Map<Integer, String> COMPANIES = Map.ofEntries(
            Map.entry(0x0002, "Intel"), Map.entry(0x0006, "Microsoft"), Map.entry(0x000D, "Texas Instruments"),
            Map.entry(0x000F, "Broadcom"), Map.entry(0x0030, "STMicroelectronics"), Map.entry(0x004C, "Apple"),
            Map.entry(0x0059, "Nordic Semiconductor"), Map.entry(0x0075, "Samsung"), Map.entry(0x0087, "Garmin"),
            Map.entry(0x00E0, "Google"), Map.entry(0x0131, "Cypress / Infineon"), Map.entry(0x0157, "Huami (Amazfit)"),
            Map.entry(0x0171, "Amazon"), Map.entry(0x01DA, "Logitech"), Map.entry(0x0310, "Sonos"),
            Map.entry(0x038F, "Xiaomi"), Map.entry(0x0499, "Ruuvi Innovations"), Map.entry(0x0822, "Adafruit"),
            Map.entry(0x02E5, "Espressif"), Map.entry(0x0118, "Radius Networks"), Map.entry(0x0046, "Sony"),
            Map.entry(0x0001, "Ericsson"), Map.entry(0x0003, "IBM"), Map.entry(0x0010, "Symbol / Zebra"),
            Map.entry(0x0397, "Shelly (Allterco)"), Map.entry(0x00D2, "Dialog Semiconductor"),
            Map.entry(0x0A0E, "Lenovo"), Map.entry(0x0110, "Nike"), Map.entry(0x0126, "Polar"),
            Map.entry(0x03DA, "Tile"), Map.entry(0x0201, "Signify (Philips Hue)"));

    /**
     * One device as last heard.
     *
     * @param rssi     dBm, null when BlueZ has no current signal (cached device)
     * @param companies "0x0499 Ruuvi Innovations" per manufacturer data entry
     * @param payload  first manufacturer data bytes as hex, for a quick look at what is broadcast
     */
    public record Device(String address, String name, Integer rssi, Integer txPower, boolean connected,
                         List<String> companies, List<String> serviceUuids, String payload, Instant lastSeen) {
        public String displayName() {
            return name == null || name.isBlank() ? "(no name)" : name;
        }
    }

    @ConfigProperty(name = "starter.hardware.simulated", defaultValue = "false")
    boolean simulated;

    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "ble-scanner");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> poller;
    private DeviceManager manager;
    private BluetoothAdapter adapter;
    private volatile String status = "Not scanning";
    private volatile boolean scanning;
    private int watchers;
    private int failures;

    public boolean isSimulated() {
        return simulated;
    }

    public boolean isScanning() {
        return scanning;
    }

    /** One line about the radio, in words a person can act on. */
    public String status() {
        return status;
    }

    /** Devices heard recently, strongest signal first. */
    public List<Device> devices() {
        Instant cutoff = Instant.now().minus(FORGET_AFTER);
        devices.values().removeIf(device -> device.lastSeen().isBefore(cutoff));
        var list = new ArrayList<>(devices.values());
        list.sort(Comparator.comparing((Device d) -> d.rssi() == null ? Integer.MIN_VALUE : d.rssi()).reversed()
                .thenComparing(Device::address));
        return list;
    }

    /** Called when the device list may have changed, from the scanner thread. */
    public Registration addListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** A panel is showing; scanning starts with the first watcher. */
    public synchronized void watch() {
        watchers++;
        start();
    }

    /** A panel went away; scanning stops with the last watcher. */
    public synchronized void unwatch() {
        watchers = Math.max(0, watchers - 1);
        if (watchers == 0) {
            stop();
        }
    }

    public synchronized void start() {
        if (scanning) {
            return;
        }
        scanning = true;
        status = simulated ? "Simulation · scanning" : "Starting";
        poller = executor.scheduleWithFixedDelay(this::poll, 0, POLL.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        if (!scanning) {
            return;
        }
        scanning = false;
        if (poller != null) {
            poller.cancel(false);
            poller = null;
        }
        executor.execute(this::disconnect);
        status = "Not scanning";
        fire();
    }

    private void poll() {
        try {
            if (simulated) {
                simulate();
            } else {
                if (adapter == null && !connect()) {
                    return;
                }
                collect();
            }
            failures = 0;
        } catch (Exception e) {
            if (failures++ == 0) {
                LOG.warnf(e, "Reading Bluetooth devices failed; will keep trying");
            } else {
                LOG.debugf(e, "Reading Bluetooth devices failed again (%d)", failures);
            }
            status = "Bluetooth error: " + e.getMessage();
            disconnect();
        }
        fire();
    }

    private boolean connect() throws Exception {
        try {
            manager = DeviceManager.createInstance(false);
        } catch (Exception e) {
            status = "BlueZ is not reachable over D-Bus (" + e.getMessage() + "). Is bluetooth.service running?";
            throw e;
        }
        adapter = manager.getAdapter();
        if (adapter == null) {
            status = "No Bluetooth adapter found. Check with: bluetoothctl list";
            disconnect();
            return false;
        }
        if (!adapter.isPowered()) {
            adapter.setPowered(true);
        }
        // Low energy only; DuplicateData makes BlueZ keep reporting devices it already knows
        manager.setScanFilter(Map.of(
                DiscoveryFilter.Transport, DiscoveryTransport.LE,
                DiscoveryFilter.DuplicateData, true));
        if (!adapter.isDiscovering() && !adapter.startDiscovery()) {
            status = "BlueZ refused to start a discovery on " + adapter.getAddress()
                    + ". Another program may be scanning, or this user may lack permission (bluetooth group).";
            return false;
        }
        status = "Scanning on " + adapter.getName() + " (" + adapter.getAddress() + ")";
        LOG.infof("BLE scan started on %s", adapter.getAddress());
        return true;
    }

    private void collect() {
        if (!adapter.isDiscovering() && !adapter.startDiscovery()) {
            status = "Not scanning: BlueZ will not start a discovery";
            return;
        }
        Instant now = Instant.now();
        // true: use what BlueZ already has, do not run a discovery of its own
        for (BluetoothDevice device : manager.getDevices(true)) {
            Short rssi = device.getRssi();
            if (rssi == null) {
                continue; // remembered by BlueZ but not currently heard
            }
            var companies = new ArrayList<String>();
            String payload = null;
            Map<?, ?> manufacturerData = device.getManufacturerData();
            if (manufacturerData != null) {
                for (Map.Entry<?, ?> entry : manufacturerData.entrySet()) {
                    int company = numberOf(entry.getKey());
                    companies.add(company(company));
                    if (payload == null) {
                        payload = hex(bytesOf(entry.getValue()), 16);
                    }
                }
            }
            String[] uuids = device.getUuids();
            Short txPower = device.getTxPower();
            Boolean connected = device.isConnected();
            String name = device.getName();
            if (name == null || name.isBlank()) {
                name = device.getAlias();
                if (name != null && name.replace('-', ':').equalsIgnoreCase(device.getAddress())) {
                    name = null; // BlueZ aliases a nameless device with its address
                }
            }
            devices.put(device.getAddress(), new Device(device.getAddress(), name, (int) rssi,
                    txPower == null ? null : (int) txPower, Boolean.TRUE.equals(connected),
                    List.copyOf(companies), uuids == null ? List.of() : List.of(uuids), payload, now));
        }
        status = "Scanning on " + adapter.getAddress() + " · " + devices().size() + " in range";
    }

    private void disconnect() {
        try {
            if (adapter != null && adapter.isDiscovering()) {
                adapter.stopDiscovery();
            }
        } catch (RuntimeException ignored) {
        }
        try {
            if (manager != null) {
                manager.closeConnection();
            }
        } catch (RuntimeException ignored) {
        }
        adapter = null;
        manager = null;
    }

    private void simulate() {
        Instant now = Instant.now();
        double t = now.getEpochSecond() / 7.0;
        devices.put("C3:A1:5E:7B:22:9F", new Device("C3:A1:5E:7B:22:9F", "Ruuvi 229F", wobble(-58, t), 4, false,
                List.of(company(0x0499)), List.of(), "05 12 FC 53 94 C3 7C 00 04 FF FC 04 0C AC 36 42", now));
        devices.put("F4:12:FA:3C:81:D0", new Device("F4:12:FA:3C:81:D0", "Thingy", wobble(-71, t + 2), null, false,
                List.of(company(0x0059)), List.of("ef680100-9b35-4933-9b10-52ffa9740042"), "59 00 01 02", now));
        devices.put("58:2D:34:0A:11:7C", new Device("58:2D:34:0A:11:7C", null, wobble(-80, t + 4), null, false,
                List.of(company(0x004C)), List.of(), "4C 00 10 05 0B 1C 3F 8A 21", now));
        devices.put("E0:5A:1B:67:42:C1", new Device("E0:5A:1B:67:42:C1", "Shelly BLU Button", wobble(-64, t + 1), null, false,
                List.of(company(0x0397)), List.of("0000fcd2-0000-1000-8000-00805f9b34fb"), "97 03 01 2A", now));
        if (Math.sin(now.getEpochSecond() / 20.0) > 0) {
            devices.put("A4:C1:38:9E:20:55", new Device("A4:C1:38:9E:20:55", "LYWSD03MMC", wobble(-88, t + 3), null, false,
                    List.of(company(0x038F)), List.of("0000181a-0000-1000-8000-00805f9b34fb"), null, now));
        }
        status = "Simulation · scanning · " + devices().size() + " in range";
    }

    private static int wobble(int base, double t) {
        return base + (int) Math.round(3 * Math.sin(t));
    }

    static String company(int id) {
        String name = COMPANIES.get(id);
        return "0x%04X".formatted(id) + (name == null ? "" : " " + name);
    }

    private static int numberOf(Object key) {
        Object value = unwrap(key);
        return value instanceof Number number ? number.intValue() : -1;
    }

    /** Manufacturer data arrives as byte[], Byte[] or a list, possibly inside a Variant. */
    private static byte[] bytesOf(Object value) {
        Object unwrapped = unwrap(value);
        if (unwrapped instanceof byte[] bytes) {
            return bytes;
        }
        if (unwrapped instanceof Byte[] boxed) {
            var bytes = new byte[boxed.length];
            for (int i = 0; i < boxed.length; i++) {
                bytes[i] = boxed[i];
            }
            return bytes;
        }
        if (unwrapped instanceof Collection<?> items) {
            var bytes = new byte[items.size()];
            int i = 0;
            for (Object item : items) {
                if (!(unwrap(item) instanceof Number number)) {
                    return new byte[0];
                }
                bytes[i++] = number.byteValue();
            }
            return bytes;
        }
        return new byte[0];
    }

    private static Object unwrap(Object value) {
        Object unwrapped = value;
        while (unwrapped instanceof Variant<?> variant) {
            unwrapped = variant.getValue();
        }
        return unwrapped;
    }

    static String hex(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        var out = new StringBuilder();
        for (int i = 0; i < Math.min(bytes.length, max); i++) {
            out.append(i > 0 ? " " : "").append("%02X".formatted(bytes[i] & 0xFF));
        }
        if (bytes.length > max) {
            out.append(" …");
        }
        return out.toString();
    }

    private void fire() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.debug("BLE listener failed", e);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        scanning = false;
        executor.shutdownNow();
        disconnect();
    }
}
