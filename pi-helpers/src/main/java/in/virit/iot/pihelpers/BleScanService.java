package in.virit.iot.pihelpers;

import com.github.hypfvieh.bluetooth.DeviceManager;
import com.github.hypfvieh.bluetooth.DiscoveryFilter;
import com.github.hypfvieh.bluetooth.DiscoveryTransport;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothAdapter;
import com.github.hypfvieh.bluetooth.wrapper.BluetoothDevice;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.freedesktop.dbus.errors.AccessDenied;
import org.freedesktop.dbus.errors.NoReply;
import org.freedesktop.dbus.errors.ServiceUnknown;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
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
 */
@ApplicationScoped
public class BleScanService {

    private static final Logger LOG = Logger.getLogger(BleScanService.class);
    static final Duration POLL = Duration.ofSeconds(2);
    /** After a failure the next attempt waits longer each time, up to this. */
    static final Duration MAX_RETRY = Duration.ofSeconds(30);
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
    private volatile boolean problem;
    private int watchers;
    private int failures;
    private String lastProblem;

    public boolean isSimulated() {
        return false;
    }

    public boolean isScanning() {
        return scanning;
    }

    /** One line about the radio, in words a person can act on. */
    public String status() {
        return status;
    }

    /** True while the last attempt failed for a reason on the host: BlueZ, the adapter, permissions. */
    public boolean hasProblem() {
        return problem;
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
        failures = 0;
        status = "Starting";
        schedule(Duration.ZERO);
    }

    /** Polls are chained rather than fixed-rate so a failing host is asked less and less often. */
    private synchronized void schedule(Duration delay) {
        if (scanning) {
            poller = executor.schedule(this::poll, delay.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private Duration retryDelay() {
        long millis = POLL.toMillis() << Math.min(failures, 6);
        return Duration.ofMillis(Math.min(millis, MAX_RETRY.toMillis()));
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
            if (adapter == null) {
                connect();
            }
            collect();
            failures = 0;
            problem = false;
            lastProblem = null;
        } catch (Exception e) {
            String message = e instanceof BluetoothProblem ? e.getMessage() : "Bluetooth scan failed: " + e;
            if (!message.equals(lastProblem)) {
                LOG.warnf(e, "%s", message);
                lastProblem = message;
            } else {
                LOG.debugf(e, "Still failing (%d): %s", failures, message);
            }
            failures++;
            status = message;
            problem = true;
            disconnect();
        } finally {
            fire();
            schedule(failures == 0 ? POLL : retryDelay());
        }
    }

    /** A host-side failure explained in words a person can act on; the panel opens the setup steps for it. */
    public static class BluetoothProblem extends RuntimeException {
        BluetoothProblem(String message) {
            super(message);
        }

        BluetoothProblem(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private interface Call<T> {
        T run() throws Exception;
    }

    /** Runs one D-Bus call and turns its failure into a message that names the step and what to do. */
    private static <T> T step(String what, Call<T> call) {
        try {
            return call.run();
        } catch (BluetoothProblem e) {
            throw e;
        } catch (Exception e) {
            throw new BluetoothProblem(explain(what, e), e);
        }
    }

    /** The D-Bus error name when there is one ("org.bluez.Error.NotReady"), else the exception's short name. */
    static String errorName(Throwable e) {
        if (e instanceof DBusExecutionException dbus && dbus.getType() != null && !dbus.getType().isBlank()) {
            return dbus.getType();
        }
        String name = e.getClass().getSimpleName();
        if (name.startsWith("Bluez") && name.endsWith("Exception")) {
            name = "org.bluez.Error." + name.substring(5, name.length() - "Exception".length());
        }
        return name;
    }

    static String explain(String what, Throwable e) {
        String name = errorName(e);
        String user = System.getProperty("user.name");
        if (e instanceof AccessDenied || name.endsWith("AccessDenied")) {
            return "D-Bus refused " + user + " access to BlueZ. Add the user to the bluetooth group and restart "
                    + "the application: sudo usermod -aG bluetooth " + user;
        }
        if (e instanceof ServiceUnknown || e instanceof NoReply || e instanceof DBusException
                || name.endsWith("ServiceUnknown") || name.endsWith("NoReply")) {
            return "BlueZ is not answering on D-Bus (" + name + "). Start it with: sudo systemctl enable --now bluetooth";
        }
        if (name.endsWith("NotReady") || name.endsWith("Blocked") || what.startsWith("Powering")) {
            return "The Bluetooth adapter is powered off or blocked by rfkill (" + name + " while " + lower(what)
                    + "). Run: sudo rfkill unblock bluetooth && bluetoothctl power on";
        }
        if (name.endsWith("InProgress") || name.endsWith("Busy")) {
            return "Another program is already scanning on this adapter; stop it or wait (check with: bluetoothctl show)";
        }
        String detail = e.getMessage() == null || e.getMessage().isBlank() || e.getMessage().equals(name)
                ? "" : ": " + e.getMessage();
        return what + " failed with " + name + detail;
    }

    private static String lower(String what) {
        return Character.toLowerCase(what.charAt(0)) + what.substring(1);
    }

    private void connect() {
        try {
            manager = DeviceManager.createInstance(false);
        } catch (Exception e) {
            throw new BluetoothProblem("BlueZ is not reachable over D-Bus (" + e.getMessage()
                    + "). Is bluetooth.service running? sudo systemctl enable --now bluetooth", e);
        }
        adapter = step("Looking for a Bluetooth adapter", manager::getAdapter);
        if (adapter == null) {
            disconnect();
            throw new BluetoothProblem("No Bluetooth adapter found. Check with: bluetoothctl list");
        }
        if (!step("Reading the adapter's power state", adapter::isPowered)) {
            step("Powering the adapter on", () -> {
                adapter.setPowered(true);
                return null;
            });
        }
        // Low energy only; DuplicateData makes BlueZ keep reporting devices it already knows
        step("Setting the LE scan filter", () -> {
            manager.setScanFilter(Map.of(
                    DiscoveryFilter.Transport, DiscoveryTransport.LE,
                    DiscoveryFilter.DuplicateData, true));
            return null;
        });
        if (!Boolean.TRUE.equals(step("Reading the discovery state", adapter::isDiscovering))) {
            // The wrapper's startDiscovery() swallows the reason; the raw interface keeps it
            step("Starting the discovery", () -> {
                adapter.getRawAdapter().StartDiscovery();
                return null;
            });
        }
        status = "Scanning on " + adapter.getName() + " (" + adapter.getAddress() + ")";
        LOG.infof("BLE scan started on %s", adapter.getAddress());
    }

    private void collect() {
        if (!Boolean.TRUE.equals(step("Reading the discovery state", adapter::isDiscovering))) {
            step("Restarting the discovery", () -> {
                adapter.getRawAdapter().StartDiscovery();
                return null;
            });
        }
        Instant now = Instant.now();
        // true: use what BlueZ already has, do not run a discovery of its own
        for (BluetoothDevice device : step("Listing devices", () -> manager.getDevices(true))) {
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
