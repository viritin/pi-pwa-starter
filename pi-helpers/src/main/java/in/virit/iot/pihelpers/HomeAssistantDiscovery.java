package in.virit.iot.pihelpers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the messages Home Assistant's MQTT discovery understands. A device
 * publishes one retained config message per entity under the discovery prefix
 * and Home Assistant creates the entities itself; a shared {@code device} block
 * groups them into one device. State goes to one JSON topic that every entity
 * reads with its own {@code value_template}; availability is a separate topic
 * whose "offline" value is also the client's last will.
 * <p>
 * Pure functions, so the payloads are easy to test without a broker.
 */
public final class HomeAssistantDiscovery {

    private HomeAssistantDiscovery() {
    }

    /**
     * One entity.
     *
     * @param objectId     stable id within the device, e.g. "temperature"
     * @param deviceClass  Home Assistant device class, e.g. "temperature"; may be null
     * @param jsonField    the key in the state JSON this entity reads
     */
    public record Sensor(String objectId, String name, String unit, String deviceClass, String jsonField) {
    }

    public record Device(String id, String name, String manufacturer, String model, String swVersion) {
    }

    public static String stateTopic(MqttSettings settings, String component) {
        return settings.topicPrefix() + "/" + settings.deviceId() + "/" + component + "/state";
    }

    public static String availabilityTopic(MqttSettings settings) {
        return settings.topicPrefix() + "/" + settings.deviceId() + "/status";
    }

    public static String configTopic(MqttSettings settings, Sensor sensor) {
        return settings.discoveryPrefix() + "/sensor/" + settings.topicPrefix() + "-" + settings.deviceId()
                + "/" + sensor.objectId() + "/config";
    }

    /**
     * The retained config message for one sensor entity.
     *
     * @param expireAfterSeconds after this long without a state message the entity becomes unavailable
     */
    public static String configPayload(MqttSettings settings, Device device, Sensor sensor, String stateTopic,
                                       int expireAfterSeconds) {
        var config = new LinkedHashMap<String, Object>();
        config.put("name", sensor.name());
        config.put("unique_id", settings.topicPrefix() + "-" + settings.deviceId() + "-" + sensor.objectId());
        config.put("object_id", settings.deviceId() + "_" + sensor.objectId());
        config.put("state_topic", stateTopic);
        config.put("value_template", "{{ value_json." + sensor.jsonField() + " }}");
        if (sensor.unit() != null) {
            config.put("unit_of_measurement", sensor.unit());
        }
        if (sensor.deviceClass() != null) {
            config.put("device_class", sensor.deviceClass());
        }
        config.put("state_class", "measurement");
        config.put("availability_topic", availabilityTopic(settings));
        config.put("payload_available", "online");
        config.put("payload_not_available", "offline");
        config.put("expire_after", expireAfterSeconds);
        var dev = new LinkedHashMap<String, Object>();
        dev.put("identifiers", List.of(settings.topicPrefix() + "-" + settings.deviceId()));
        dev.put("name", device.name());
        dev.put("manufacturer", device.manufacturer());
        dev.put("model", device.model());
        if (device.swVersion() != null) {
            dev.put("sw_version", device.swVersion());
        }
        config.put("device", dev);
        return Json.write(config);
    }

    /** A state message from field name to value; nulls are left out. */
    public static String statePayload(Map<String, ?> values) {
        var state = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> {
            if (value != null) {
                state.put(key, value);
            }
        });
        return Json.write(state);
    }
}
