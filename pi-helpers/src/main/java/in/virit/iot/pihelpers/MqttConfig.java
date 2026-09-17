package in.virit.iot.pihelpers;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

/**
 * MQTT settings from {@code application.properties} ({@code starter.mqtt.*}),
 * typed by SmallRye Config. A finished application usually configures its
 * broker this way rather than from a web page; when {@code starter.mqtt.host}
 * is set, these settings win and the UI only shows what is going on.
 *
 * <pre>
 * starter.mqtt.host=homeassistant.local
 * starter.mqtt.port=1883
 * starter.mqtt.username=pi
 * starter.mqtt.password=secret
 * starter.mqtt.device-id=pi-kitchen
 * starter.mqtt.topic-prefix=pi-starter
 * starter.mqtt.discovery-prefix=homeassistant
 * starter.mqtt.interval=30          # seconds, or a duration such as 2m
 * starter.mqtt.enabled=true
 * </pre>
 */
@ConfigMapping(prefix = "starter.mqtt")
public interface MqttConfig {

    Optional<String> host();

    @WithDefault("1883")
    int port();

    Optional<String> username();

    Optional<String> password();

    Optional<String> deviceId();

    @WithDefault("pi-starter")
    String topicPrefix();

    @WithDefault("homeassistant")
    String discoveryPrefix();

    @WithDefault("30")
    Duration interval();

    @WithDefault("true")
    boolean enabled();

    /** True when a broker host is configured, i.e. configuration overrides the UI. */
    default boolean isPresent() {
        return host().filter(h -> !h.isBlank()).isPresent();
    }

    /** The configured settings, or empty when no host is set. */
    default Optional<MqttSettings> settings(String defaultDeviceId) {
        return host().filter(h -> !h.isBlank()).map(h -> new MqttSettings(enabled(), h.trim(), port(),
                username().orElse(""), password().orElse(""),
                deviceId().filter(id -> !id.isBlank()).orElse(defaultDeviceId), topicPrefix(), discoveryPrefix(),
                interval().compareTo(MqttSettings.MIN_INTERVAL) < 0 ? MqttSettings.MIN_INTERVAL : interval()));
    }
}
