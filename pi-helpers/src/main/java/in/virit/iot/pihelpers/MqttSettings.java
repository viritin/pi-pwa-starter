package in.virit.iot.pihelpers;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Duration;

/**
 * Where and how to publish over MQTT. A plain record that {@link SettingsStore}
 * keeps as JSON; the password is stored as is, which suits a home network and
 * is said so in the README.
 *
 * @param enabled         whether publishing should be running
 * @param topicPrefix     first topic segment, e.g. "pi-starter"
 * @param discoveryPrefix Home Assistant's discovery prefix, normally "homeassistant"
 * @param interval        how often a state message goes out
 */
public record MqttSettings(boolean enabled, String host, int port, String username, String password,
                           String deviceId, String topicPrefix, String discoveryPrefix, Duration interval) {

    public static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(30);
    /** Home Assistant marks an entity unavailable this soon after the last state, so faster makes no sense. */
    public static final Duration MIN_INTERVAL = Duration.ofSeconds(5);

    public static MqttSettings defaults(String deviceId) {
        return new MqttSettings(false, "", HomeAssistantFinder.MQTT_PORT, "", "", deviceId,
                "pi-starter", "homeassistant", DEFAULT_INTERVAL);
    }

    public MqttSettings withBroker(String host, int port) {
        return new MqttSettings(enabled, host, port, username, password, deviceId, topicPrefix, discoveryPrefix, interval);
    }

    public MqttSettings withCredentials(String username, String password) {
        return new MqttSettings(enabled, host, port, username, password, deviceId, topicPrefix, discoveryPrefix, interval);
    }

    public MqttSettings withEnabled(boolean enabled) {
        return new MqttSettings(enabled, host, port, username, password, deviceId, topicPrefix, discoveryPrefix, interval);
    }

    @JsonIgnore
    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    @JsonIgnore
    public boolean hasBroker() {
        return host != null && !host.isBlank();
    }

    /** The client id on the broker and the device id in Home Assistant, unique per prefix and device. */
    @JsonIgnore
    public String clientId() {
        return topicPrefix + "-" + deviceId;
    }
}
