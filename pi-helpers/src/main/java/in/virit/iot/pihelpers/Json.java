package in.virit.iot.pihelpers;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The helpers' one JSON mapper: Jackson 3, which Vaadin 25 already brings, so
 * payloads and settings are plain records with annotations rather than maps.
 * Dates go out as ISO-8601 strings, the form Home Assistant templates expect.
 */
public final class Json {

    public static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private Json() {
    }

    public static String write(Object value) {
        return MAPPER.writeValueAsString(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return MAPPER.readValue(json, type);
    }
}
