package in.virit.iot.pihelpers;

import java.util.Properties;

/**
 * Where and how to publish over MQTT. Plain values, stored through
 * {@link SettingsStore}; the password is kept as is, which suits a home
 * network and is said so in the README.
 *
 * @param enabled         whether publishing should be running
 * @param topicPrefix     first topic segment, e.g. "pi-starter"
 * @param discoveryPrefix Home Assistant's discovery prefix, normally "homeassistant"
 * @param intervalSeconds how often a state message goes out
 */
public record MqttSettings(boolean enabled, String host, int port, String username, String password,
                           String deviceId, String topicPrefix, String discoveryPrefix, int intervalSeconds) {

    public static final int DEFAULT_INTERVAL = 30;

    public static MqttSettings defaults(String deviceId) {
        return new MqttSettings(false, "", HomeAssistantFinder.MQTT_PORT, "", "", deviceId, "pi-starter", "homeassistant", DEFAULT_INTERVAL);
    }

    public MqttSettings withBroker(String host, int port) {
        return new MqttSettings(enabled, host, port, username, password, deviceId, topicPrefix, discoveryPrefix, intervalSeconds);
    }

    public MqttSettings withCredentials(String username, String password) {
        return new MqttSettings(enabled, host, port, username, password, deviceId, topicPrefix, discoveryPrefix, intervalSeconds);
    }

    public MqttSettings withEnabled(boolean enabled) {
        return new MqttSettings(enabled, host, port, username, password, deviceId, topicPrefix, discoveryPrefix, intervalSeconds);
    }

    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    public boolean hasBroker() {
        return host != null && !host.isBlank();
    }

    public Properties toProperties() {
        var p = new Properties();
        p.setProperty("enabled", String.valueOf(enabled));
        p.setProperty("host", host == null ? "" : host);
        p.setProperty("port", String.valueOf(port));
        p.setProperty("username", username == null ? "" : username);
        p.setProperty("password", password == null ? "" : password);
        p.setProperty("deviceId", deviceId == null ? "" : deviceId);
        p.setProperty("topicPrefix", topicPrefix);
        p.setProperty("discoveryPrefix", discoveryPrefix);
        p.setProperty("intervalSeconds", String.valueOf(intervalSeconds));
        return p;
    }

    public static MqttSettings from(Properties p, String defaultDeviceId) {
        var defaults = defaults(defaultDeviceId);
        return new MqttSettings(
                Boolean.parseBoolean(p.getProperty("enabled", "false")),
                p.getProperty("host", defaults.host()),
                parseInt(p.getProperty("port"), defaults.port()),
                p.getProperty("username", ""),
                p.getProperty("password", ""),
                p.getProperty("deviceId", defaultDeviceId),
                p.getProperty("topicPrefix", defaults.topicPrefix()),
                p.getProperty("discoveryPrefix", defaults.discoveryPrefix()),
                parseInt(p.getProperty("intervalSeconds"), defaults.intervalSeconds()));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
