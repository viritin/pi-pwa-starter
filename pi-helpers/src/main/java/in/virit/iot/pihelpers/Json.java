package in.virit.iot.pihelpers;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON writing for MQTT payloads: maps, lists, strings, numbers,
 * booleans and null. Keeps the helpers free of a JSON library dependency.
 */
public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        var out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String text -> quote(out, text);
            case Boolean bool -> out.append(bool);
            case Number number -> out.append(number instanceof Double || number instanceof Float
                    ? number.toString() : String.valueOf(number.longValue()));
            case Map<?, ?> map -> {
                out.append('{');
                for (Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator(); it.hasNext(); ) {
                    var entry = it.next();
                    quote(out, String.valueOf(entry.getKey()));
                    out.append(':');
                    append(out, entry.getValue());
                    if (it.hasNext()) {
                        out.append(',');
                    }
                }
                out.append('}');
            }
            case List<?> list -> {
                out.append('[');
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    append(out, list.get(i));
                }
                out.append(']');
            }
            default -> quote(out, value.toString());
        }
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
