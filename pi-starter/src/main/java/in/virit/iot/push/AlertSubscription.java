package in.virit.iot.push;

import java.time.Instant;

/**
 * One browser's push subscription and the temperature range it wants to hear
 * about. {@code clientId} is a random id the browser keeps in localStorage, so a
 * phone and a laptop each have their own; {@code username} is set when sign-in
 * is on and someone was signed in when subscribing.
 */
public record AlertSubscription(String clientId, String endpoint, String p256dh, String auth,
                                Double min, Double max, String username, Instant createdAt) {

    public AlertSubscription withLimits(Double min, Double max) {
        return new AlertSubscription(clientId, endpoint, p256dh, auth, min, max, username, createdAt);
    }
}
