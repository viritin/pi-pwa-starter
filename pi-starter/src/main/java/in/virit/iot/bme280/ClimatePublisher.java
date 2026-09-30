package in.virit.iot.bme280;

import com.vaadin.flow.shared.Registration;
import com.fasterxml.jackson.annotation.JsonInclude;
import in.virit.iot.pihelpers.BoardInfo;
import in.virit.iot.pihelpers.HomeAssistantDiscovery;
import in.virit.iot.pihelpers.HomeAssistantDiscovery.Device;
import in.virit.iot.pihelpers.HomeAssistantDiscovery.Sensor;
import in.virit.iot.pihelpers.MqttConfig;
import in.virit.iot.pihelpers.MqttPublisher;
import in.virit.iot.pihelpers.MqttSettings;
import in.virit.iot.pihelpers.SettingsStore;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.virit.iot.pihelpers.HomeAssistantDiscovery.OFFLINE;
import static in.virit.iot.pihelpers.HomeAssistantDiscovery.ONLINE;
import static in.virit.iot.pihelpers.HomeAssistantDiscovery.availabilityTopic;
import static in.virit.iot.pihelpers.HomeAssistantDiscovery.configTopic;
import static in.virit.iot.pihelpers.HomeAssistantDiscovery.stateTopic;

/**
 * Shares the climate readings with Home Assistant over MQTT. On connect it
 * publishes one retained discovery message per sensor and "online" on the
 * availability topic; Home Assistant then creates a device with the three
 * entities by itself. State follows at the configured interval. The client's
 * last will marks the device offline when this application disappears.
 * <p>
 * An example of publishing data to another system: the same shape works for
 * any sensor service, only the sensors and the state JSON differ. Settings come
 * from {@code starter.mqtt.*} when present, otherwise from what the Climate view
 * saved through {@link SettingsStore}.
 */
@ApplicationScoped
public class ClimatePublisher {

    private static final Logger LOG = Logger.getLogger(ClimatePublisher.class);
    static final String SETTINGS = "homeassistant";
    static final String COMPONENT = "climate";
    private static final Sensor TEMPERATURE = new Sensor("temperature", "Temperature", "°C", "temperature", "temperature");
    private static final Sensor HUMIDITY = new Sensor("humidity", "Humidity", "%", "humidity", "humidity");
    private static final Sensor PRESSURE = new Sensor("pressure", "Pressure", "hPa", "atmospheric_pressure", "pressure");
    private static final List<Sensor> SENSORS = List.of(TEMPERATURE, HUMIDITY, PRESSURE);

    /** The state message; the field names are what the sensors' {@code jsonField}s read. Humidity is absent on a BMP280. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ClimateState(double temperature, Double humidity, double pressure, Instant at) {
        static ClimateState of(Bme280Service.Reading reading) {
            return new ClimateState(reading.temperature(), reading.humidity(), reading.pressure(), reading.at());
        }
    }

    @Inject
    Bme280Service sensor;
    @Inject
    MqttPublisher mqtt;
    @Inject
    SettingsStore store;
    @Inject
    MqttConfig config;
    @ConfigProperty(name = "quarkus.application.version", defaultValue = "dev")
    String version;

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile MqttSettings settings;
    private volatile Instant lastPublished;
    private volatile String lastError;
    private volatile boolean removeOnConnect;
    private ScheduledExecutorService executor;

    void onStart(@Observes StartupEvent event) {
        // application.properties wins over what was saved from the UI
        String deviceId = defaultDeviceId();
        settings = config.settings(deviceId)
                .or(() -> store.load(SETTINGS, MqttSettings.class))
                .orElseGet(() -> MqttSettings.defaults(deviceId));
        mqtt.addListener(this::onMqttState);
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "climate-publisher");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tick, 5, 5, TimeUnit.SECONDS);
        if (settings.enabled() && settings.hasBroker()) {
            connect();
        }
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        sayGoodbye();
    }

    /** Connects with "offline" as the last will, so the broker marks the device unavailable if we vanish. */
    private void connect() {
        mqtt.connect(settings, availabilityTopic(settings), OFFLINE);
    }

    private void sayGoodbye() {
        if (mqtt.isConnected()) {
            try {
                mqtt.publish(availabilityTopic(settings), OFFLINE, true);
            } catch (RuntimeException e) {
                LOG.debug("Could not publish offline", e);
            }
        }
    }

    public MqttSettings settings() {
        return settings;
    }

    /** True when starter.mqtt.* is set; the UI then shows the state but cannot change the settings. */
    public boolean isConfiguredExternally() {
        return config.isPresent();
    }

    public MqttPublisher.State state() {
        return settings.enabled() ? mqtt.state() : MqttPublisher.State.DISCONNECTED;
    }

    public Instant lastPublished() {
        return lastPublished;
    }

    /** One line for the UI: what the connection is doing and when data last went out. */
    public String status() {
        if (removeOnConnect) {
            return "Removing from Home Assistant…";
        }
        if (!settings.enabled()) {
            return "Not sharing";
        }
        if (mqtt.state() == MqttPublisher.State.CONNECTED) {
            String base = "Publishing to " + settings.host() + " every " + settings.interval().toSeconds() + " s";
            if (lastError != null) {
                return base + " · last attempt failed: " + lastError;
            }
            return lastPublished == null ? base
                    : base + " · last reading " + Duration.between(lastPublished, Instant.now()).toSeconds() + " s ago";
        }
        return mqtt.status();
    }

    public Registration addListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /** Saves the settings and connects; discovery and the first state go out once connected. */
    public synchronized void start(MqttSettings newSettings) {
        requireEditable();
        settings = newSettings.withEnabled(true);
        store.save(SETTINGS, settings);
        lastError = null;
        connect();
        fire();
    }

    /** Says goodbye and disconnects; the entities stay in Home Assistant as unavailable. */
    public synchronized void stopSharing() {
        requireEditable();
        sayGoodbye();
        mqtt.disconnect();
        settings = settings.withEnabled(false);
        store.save(SETTINGS, settings);
        fire();
    }

    /**
     * Clears the retained discovery messages so Home Assistant removes the device,
     * then stops. Connects first when sharing is already stopped, since clearing
     * needs the broker.
     */
    public synchronized void removeFromHomeAssistant() {
        requireEditable();
        if (!settings.hasBroker()) {
            return;
        }
        if (mqtt.isConnected()) {
            clearDiscovery();
            stopSharing();
            return;
        }
        removeOnConnect = true;
        connect();
        fire();
    }

    private void clearDiscovery() {
        for (Sensor s : SENSORS) {
            try {
                mqtt.clear(configTopic(settings, s));
            } catch (RuntimeException e) {
                LOG.warnf("Could not clear discovery for %s: %s", s.objectId(), e.getMessage());
            }
        }
        LOG.infof("Cleared Home Assistant discovery for %s", settings.deviceId());
    }

    private void requireEditable() {
        if (config.isPresent()) {
            throw new IllegalStateException("MQTT is configured in application.properties (starter.mqtt.*); change it there.");
        }
    }

    private void onMqttState() {
        if (removeOnConnect) {
            if (mqtt.isConnected()) {
                removeOnConnect = false;
                clearDiscovery();
                stopSharing();
            } else if (mqtt.state() == MqttPublisher.State.ERROR || mqtt.state() == MqttPublisher.State.UNAUTHORIZED) {
                removeOnConnect = false;
            }
        } else if (settings.enabled() && mqtt.isConnected()) {
            announce();
            publishState();
        }
        fire();
    }

    private void tick() {
        if (settings.enabled() && mqtt.isConnected()) {
            Instant last = lastPublished;
            if (last == null || Duration.between(last, Instant.now()).compareTo(settings.interval()) >= 0) {
                publishState();
                fire();
            }
        }
    }

    private void announce() {
        var device = new Device(settings.deviceId(), "Pi Starter " + hostname(), "Raspberry Pi", model(), version);
        String stateTopic = stateTopic(settings, COMPONENT);
        int expire = (int) Math.max(60, settings.interval().toSeconds() * 3);
        boolean humidity = sensor.latest().map(r -> r.humidity() != null).orElse(true);
        try {
            for (Sensor s : SENSORS) {
                if (s == HUMIDITY && !humidity) {
                    mqtt.clear(configTopic(settings, s)); // a BMP280 has no humidity; withdraw the entity
                } else {
                    mqtt.publishJson(configTopic(settings, s),
                            HomeAssistantDiscovery.config(settings, device, s, stateTopic, expire), true);
                }
            }
            mqtt.publish(availabilityTopic(settings), ONLINE, true);
            lastError = null;
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            LOG.warnf("Announcing to Home Assistant failed: %s", e.getMessage());
        }
    }

    private void publishState() {
        var latest = sensor.latest();
        if (latest.isEmpty()) {
            return;
        }
        try {
            mqtt.publishJson(stateTopic(settings, COMPONENT), ClimateState.of(latest.get()), true);
            lastPublished = Instant.now();
            lastError = null;
        } catch (RuntimeException e) {
            lastError = e.getMessage();
            LOG.warnf("Publishing climate state failed: %s", e.getMessage());
        }
    }

    private void fire() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.debug("Publisher listener failed", e);
            }
        }
    }

    /** The Pi's serial number when available, otherwise the host name; lower case, safe in topics. */
    static String defaultDeviceId() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.startsWith("Serial")) {
                    String serial = line.substring(line.indexOf(':') + 1).trim().toLowerCase(Locale.ROOT);
                    if (serial.length() >= 8) {
                        return "pi-" + serial.substring(serial.length() - 8);
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return hostname().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "pi";
        }
    }

    private static String model() {
        var model = BoardInfo.detect().model();
        return model != null ? model : "Pi Starter";
    }
}
