package in.virit.iot;

import in.virit.iot.pihelpers.HomeAssistantDiscovery;
import in.virit.iot.pihelpers.HomeAssistantDiscovery.Device;
import in.virit.iot.pihelpers.HomeAssistantDiscovery.Sensor;
import in.virit.iot.pihelpers.Json;
import in.virit.iot.pihelpers.MqttSettings;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The messages Home Assistant sees, checked without a broker. */
class HomeAssistantDiscoveryTest {

    private final MqttSettings settings = MqttSettings.defaults("pi-1234abcd").withBroker("10.0.0.5", 1883);
    private final Device device = new Device("pi-1234abcd", "Pi Starter kitchen", "Raspberry Pi", "Raspberry Pi 5", "1.0");
    private final Sensor temperature = new Sensor("temperature", "Temperature", "°C", "temperature", "temperature");
    private final Sensor pressure = new Sensor("pressure", "Pressure", "hPa", null, "pressure");

    @Test
    void topicsFollowThePrefixAndDeviceId() {
        assertEquals("pi-starter/pi-1234abcd/climate/state", HomeAssistantDiscovery.stateTopic(settings, "climate"));
        assertEquals("pi-starter/pi-1234abcd/status", HomeAssistantDiscovery.availabilityTopic(settings));
        assertEquals("homeassistant/sensor/pi-starter-pi-1234abcd/temperature/config",
                HomeAssistantDiscovery.configTopic(settings, temperature));
    }

    @Test
    void configMessageCarriesWhatDiscoveryNeeds() {
        JsonNode json = Json.MAPPER.readTree(Json.write(HomeAssistantDiscovery.config(settings, device, temperature,
                HomeAssistantDiscovery.stateTopic(settings, "climate"), 90)));
        assertEquals("Temperature", json.path("name").asString());
        assertEquals("pi-starter-pi-1234abcd-temperature", json.path("unique_id").asString());
        assertEquals("pi-starter/pi-1234abcd/climate/state", json.path("state_topic").asString());
        assertEquals("{{ value_json.temperature }}", json.path("value_template").asString());
        assertEquals("°C", json.path("unit_of_measurement").asString());
        assertEquals("temperature", json.path("device_class").asString());
        assertEquals("measurement", json.path("state_class").asString());
        assertEquals("pi-starter/pi-1234abcd/status", json.path("availability_topic").asString());
        assertEquals("online", json.path("payload_available").asString());
        assertEquals(90, json.path("expire_after").asInt());
        assertEquals("pi-starter-pi-1234abcd", json.path("device").path("identifiers").get(0).asString(),
                "the shared device block is what groups the entities");
        assertEquals("Pi Starter kitchen", json.path("device").path("name").asString());
        assertEquals("1.0", json.path("device").path("sw_version").asString());
    }

    @Test
    void optionalFieldsAreLeftOutRatherThanNull() {
        String json = Json.write(HomeAssistantDiscovery.config(settings, device, pressure, "t", 60));
        assertFalse(json.contains("device_class"), json);
        assertTrue(json.contains("\"unit_of_measurement\":\"hPa\""), json);
    }

    @Test
    void stateMessagesUseIsoTimestamps() {
        record State(double temperature, Double humidity, Instant at) {
        }
        String json = Json.write(new State(21.5, null, Instant.parse("2026-09-17T08:30:00Z")));
        assertEquals("{\"temperature\":21.5,\"humidity\":null,\"at\":\"2026-09-17T08:30:00Z\"}", json,
                "without @JsonInclude a null is written; the climate state record opts out of that");
    }

    @Test
    void settingsSurviveAJsonRoundTrip() {
        var original = MqttSettings.defaults("pi-1").withBroker("ha.local", 1884).withCredentials("mqtt", "s3cret")
                .withEnabled(true);
        String json = Json.write(original);
        assertEquals(original, Json.read(json, MqttSettings.class));
        assertFalse(json.contains("hasCredentials"), "derived accessors are not fields: " + json);
        assertEquals(Duration.ofSeconds(30), original.interval());
    }
}
