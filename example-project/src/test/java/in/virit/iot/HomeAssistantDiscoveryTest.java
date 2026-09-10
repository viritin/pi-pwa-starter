package in.virit.iot;

import in.virit.iot.pihelpers.HomeAssistantDiscovery;
import in.virit.iot.pihelpers.HomeAssistantDiscovery.Device;
import in.virit.iot.pihelpers.HomeAssistantDiscovery.Sensor;
import in.virit.iot.pihelpers.Json;
import in.virit.iot.pihelpers.MqttSettings;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The messages Home Assistant sees, checked without a broker. */
class HomeAssistantDiscoveryTest {

    private final MqttSettings settings = MqttSettings.defaults("pi-1234abcd").withBroker("10.0.0.5", 1883);
    private final Device device = new Device("pi-1234abcd", "Pi Starter kitchen", "Raspberry Pi", "Raspberry Pi 5", "1.0");
    private final Sensor temperature = new Sensor("temperature", "Temperature", "°C", "temperature", "temperature");

    @Test
    void topicsFollowThePrefixAndDeviceId() {
        assertEquals("pi-starter/pi-1234abcd/climate/state", HomeAssistantDiscovery.stateTopic(settings, "climate"));
        assertEquals("pi-starter/pi-1234abcd/status", HomeAssistantDiscovery.availabilityTopic(settings));
        assertEquals("homeassistant/sensor/pi-starter-pi-1234abcd/temperature/config",
                HomeAssistantDiscovery.configTopic(settings, temperature));
    }

    @Test
    void configMessageCarriesWhatDiscoveryNeeds() {
        String json = HomeAssistantDiscovery.configPayload(settings, device, temperature,
                HomeAssistantDiscovery.stateTopic(settings, "climate"), 90);
        assertTrue(json.startsWith("{\"name\":\"Temperature\""));
        assertTrue(json.contains("\"unique_id\":\"pi-starter-pi-1234abcd-temperature\""));
        assertTrue(json.contains("\"state_topic\":\"pi-starter/pi-1234abcd/climate/state\""));
        assertTrue(json.contains("\"value_template\":\"{{ value_json.temperature }}\""));
        assertTrue(json.contains("\"unit_of_measurement\":\"°C\""));
        assertTrue(json.contains("\"device_class\":\"temperature\""));
        assertTrue(json.contains("\"state_class\":\"measurement\""));
        assertTrue(json.contains("\"availability_topic\":\"pi-starter/pi-1234abcd/status\""));
        assertTrue(json.contains("\"expire_after\":90"));
        assertTrue(json.contains("\"device\":{\"identifiers\":[\"pi-starter-pi-1234abcd\"],\"name\":\"Pi Starter kitchen\""),
                "the shared device block is what groups the entities");
    }

    @Test
    void stateMessageDropsMissingValues() {
        var values = new LinkedHashMap<String, Object>();
        values.put("temperature", 21.5);
        values.put("humidity", null);
        values.put("pressure", 1012.3);
        assertEquals("{\"temperature\":21.5,\"pressure\":1012.3}", HomeAssistantDiscovery.statePayload(values));
    }

    @Test
    void jsonEscapesAndTypes() {
        var value = new LinkedHashMap<String, Object>();
        value.put("n", "a \"q\" \\ \n");
        value.put("i", 3);
        value.put("d", 2.5);
        value.put("b", true);
        value.put("l", List.of(1, "x"));
        value.put("z", null);
        assertEquals("{\"n\":\"a \\\"q\\\" \\\\ \\n\",\"i\":3,\"d\":2.5,\"b\":true,\"l\":[1,\"x\"],\"z\":null}",
                Json.write(value));
    }

    @Test
    void settingsSurviveAPropertiesRoundTrip() {
        var original = MqttSettings.defaults("pi-1").withBroker("ha.local", 1884).withCredentials("mqtt", "s3cret").withEnabled(true);
        var restored = MqttSettings.from(original.toProperties(), "other");
        assertEquals(original, restored);
        assertEquals("other", MqttSettings.from(new java.util.Properties(), "other").deviceId(),
                "an empty file falls back to the generated device id");
    }
}
