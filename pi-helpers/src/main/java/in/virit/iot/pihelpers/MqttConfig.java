package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

/**
 * MQTT settings from {@code application.properties} ({@code starter.mqtt.*}).
 * A finished application usually configures its broker this way rather than
 * from a web page; when {@code starter.mqtt.host} is set, these settings win
 * and the UI only shows what is going on.
 *
 * <pre>
 * starter.mqtt.host=homeassistant.local
 * starter.mqtt.port=1883
 * starter.mqtt.username=pi
 * starter.mqtt.password=secret
 * starter.mqtt.device-id=pi-kitchen
 * starter.mqtt.topic-prefix=pi-starter
 * starter.mqtt.discovery-prefix=homeassistant
 * starter.mqtt.interval=30
 * starter.mqtt.enabled=true
 * </pre>
 */
@ApplicationScoped
public class MqttConfig {

    @ConfigProperty(name = "starter.mqtt.host")
    Optional<String> host;
    @ConfigProperty(name = "starter.mqtt.port", defaultValue = "1883")
    int port;
    @ConfigProperty(name = "starter.mqtt.username")
    Optional<String> username;
    @ConfigProperty(name = "starter.mqtt.password")
    Optional<String> password;
    @ConfigProperty(name = "starter.mqtt.device-id")
    Optional<String> deviceId;
    @ConfigProperty(name = "starter.mqtt.topic-prefix", defaultValue = "pi-starter")
    String topicPrefix;
    @ConfigProperty(name = "starter.mqtt.discovery-prefix", defaultValue = "homeassistant")
    String discoveryPrefix;
    @ConfigProperty(name = "starter.mqtt.interval", defaultValue = "30")
    int intervalSeconds;
    @ConfigProperty(name = "starter.mqtt.enabled", defaultValue = "true")
    boolean enabled;

    /** True when a broker host is configured, i.e. configuration overrides the UI. */
    public boolean isPresent() {
        return host.isPresent() && !host.get().isBlank();
    }

    /** The configured settings, or empty when no host is set. */
    public Optional<MqttSettings> settings(String defaultDeviceId) {
        if (!isPresent()) {
            return Optional.empty();
        }
        return Optional.of(new MqttSettings(enabled, host.get().trim(), port, username.orElse(""), password.orElse(""),
                deviceId.filter(id -> !id.isBlank()).orElse(defaultDeviceId), topicPrefix, discoveryPrefix,
                Math.max(5, intervalSeconds)));
    }
}
