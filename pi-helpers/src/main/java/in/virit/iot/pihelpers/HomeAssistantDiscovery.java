package in.virit.iot.pihelpers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The messages Home Assistant's MQTT discovery understands, as records that
 * serialize to exactly the JSON its documentation shows. A device publishes one
 * retained config message per entity under the discovery prefix and Home
 * Assistant creates the entities itself; a shared {@code device} block groups
 * them into one device. State goes to one JSON topic that every entity reads
 * with its own {@code value_template}; availability is a separate topic whose
 * "offline" value is also the client's last will.
 */
public final class HomeAssistantDiscovery {

    public static final String ONLINE = "online";
    public static final String OFFLINE = "offline";

    private HomeAssistantDiscovery() {
    }

    /**
     * One entity.
     *
     * @param objectId    stable id within the device, e.g. "temperature"
     * @param deviceClass Home Assistant device class, e.g. "temperature"; may be null
     * @param jsonField   the key in the state JSON this entity reads
     */
    public record Sensor(String objectId, String name, String unit, String deviceClass, String jsonField) {
    }

    public record Device(String id, String name, String manufacturer, String model, String swVersion) {
    }

    /** The {@code device} block shared by every entity of one device. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeviceInfo(List<String> identifiers, String name, String manufacturer, String model,
                             @JsonProperty("sw_version") String swVersion) {
    }

    /** The retained config message of one sensor entity, field names as in Home Assistant's documentation. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SensorConfig(String name,
                               @JsonProperty("unique_id") String uniqueId,
                               @JsonProperty("object_id") String objectId,
                               @JsonProperty("state_topic") String stateTopic,
                               @JsonProperty("value_template") String valueTemplate,
                               @JsonProperty("unit_of_measurement") String unitOfMeasurement,
                               @JsonProperty("device_class") String deviceClass,
                               @JsonProperty("state_class") String stateClass,
                               @JsonProperty("availability_topic") String availabilityTopic,
                               @JsonProperty("payload_available") String payloadAvailable,
                               @JsonProperty("payload_not_available") String payloadNotAvailable,
                               @JsonProperty("expire_after") int expireAfter,
                               DeviceInfo device) {
    }

    public static String stateTopic(MqttSettings settings, String component) {
        return settings.topicPrefix() + "/" + settings.deviceId() + "/" + component + "/state";
    }

    public static String availabilityTopic(MqttSettings settings) {
        return settings.topicPrefix() + "/" + settings.deviceId() + "/status";
    }

    public static String configTopic(MqttSettings settings, Sensor sensor) {
        return settings.discoveryPrefix() + "/sensor/" + settings.clientId() + "/" + sensor.objectId() + "/config";
    }

    /**
     * The config message for one sensor entity.
     *
     * @param expireAfterSeconds after this long without a state message the entity becomes unavailable
     */
    public static SensorConfig config(MqttSettings settings, Device device, Sensor sensor, String stateTopic,
                                      int expireAfterSeconds) {
        return new SensorConfig(sensor.name(),
                settings.clientId() + "-" + sensor.objectId(),
                settings.deviceId() + "_" + sensor.objectId(),
                stateTopic,
                "{{ value_json." + sensor.jsonField() + " }}",
                sensor.unit(), sensor.deviceClass(), "measurement",
                availabilityTopic(settings), ONLINE, OFFLINE, expireAfterSeconds,
                new DeviceInfo(List.of(settings.clientId()), device.name(), device.manufacturer(), device.model(),
                        device.swVersion()));
    }
}
