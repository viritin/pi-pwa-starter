package in.virit.iot.diagnostics;

import java.time.Instant;

/** A bounded, user-visible record of a server-side failure. */
public record Incident(Instant time, String source, String thread, String summary, String details) {
    public String displayText() {
        return summary + "\n\n" + details;
    }
}
