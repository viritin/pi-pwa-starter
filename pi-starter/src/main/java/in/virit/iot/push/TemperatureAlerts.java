package in.virit.iot.push;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.webpush.WebPushState;
import in.virit.iot.bme280.Bme280Service;
import in.virit.iot.bme280.Bme280Service.Reading;
import in.virit.iot.pihelpers.AppInfo;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimum and maximum temperature alerts over Web Push, and what the Climate view
 * needs to manage a browser's subscription. Each browser has its own range. A
 * notification goes out once when the temperature leaves the range; it is sent
 * again only after the reading has come back inside by {@link #REARM}, so a value
 * hovering at a limit does not flood the phone.
 */
@ApplicationScoped
public class TemperatureAlerts {

    /** How far back inside the range a reading must come before the alert re-arms. */
    static final double REARM = 0.5;

    enum Zone { OK, TOO_COLD, TOO_WARM }

    /** The zone after a reading, and whether entering it is news. */
    record Outcome(Zone zone, boolean announce) {
    }

    @Inject
    Bme280Service climate;
    @Inject
    PushSubscriptionStore store;
    @Inject
    WebPushSender sender;
    @Inject
    AppInfo appInfo;

    /** The last zone per browser; in memory, so a restart may repeat one alert. */
    private final Map<String, Zone> zones = new ConcurrentHashMap<>();

    void start(@Observes StartupEvent event) {
        climate.addListener(this::check);
    }

    static Outcome evaluate(Zone previous, double temperature, Double min, Double max) {
        Zone now;
        if (max != null && temperature > max) {
            now = Zone.TOO_WARM;
        } else if (min != null && temperature < min) {
            now = Zone.TOO_COLD;
        } else if (previous == Zone.TOO_WARM && max != null && temperature > max - REARM) {
            now = Zone.TOO_WARM;
        } else if (previous == Zone.TOO_COLD && min != null && temperature < min + REARM) {
            now = Zone.TOO_COLD;
        } else {
            now = Zone.OK;
        }
        return new Outcome(now, now != Zone.OK && now != previous);
    }

    void check(Reading reading) {
        for (var subscription : store.all()) {
            if (subscription.min() == null && subscription.max() == null) {
                continue;
            }
            var outcome = evaluate(zones.getOrDefault(subscription.clientId(), Zone.OK),
                    reading.temperature(), subscription.min(), subscription.max());
            zones.put(subscription.clientId(), outcome.zone());
            if (outcome.announce()) {
                boolean warm = outcome.zone() == Zone.TOO_WARM;
                sender.send(subscription, appInfo.name() + ": " + (warm ? "too warm" : "too cold"),
                        "%s, %s your %s of %s".formatted(celsius(reading.temperature()),
                                warm ? "above" : "below", warm ? "max" : "min",
                                celsius(warm ? subscription.max() : subscription.min())));
            }
        }
    }

    static String celsius(double value) {
        String number = value == Math.rint(value) ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
        return number + " °C";
    }

    // --- for the Climate view ----------------------------------------------

    public String publicKey() {
        return sender.publicKey();
    }

    public Optional<AlertSubscription> find(String clientId) {
        return store.find(clientId);
    }

    /** Whether this browser has a push subscription, and so whether the switch is on. */
    public void subscriptionExists(UI ui, WebPushState receiver) {
        sender.webPush().subscriptionExists(ui, receiver);
    }

    public void subscribe(String clientId, String endpoint, String p256dh, String auth,
                          Double min, Double max, String username) {
        store.save(new AlertSubscription(clientId, endpoint, p256dh, auth, min, max, username, Instant.now()));
        zones.remove(clientId);
    }

    public void unsubscribe(String clientId) {
        store.delete(clientId);
        zones.remove(clientId);
    }

    /** New limits are judged afresh: an alert already outside them is announced again. */
    public void setLimits(String clientId, Double min, Double max) {
        store.find(clientId).ifPresent(s -> store.save(s.withLimits(min, max)));
        zones.remove(clientId);
    }

    public void sendTest(String clientId) {
        store.find(clientId).ifPresent(s -> sender.send(s, appInfo.name(),
                "Test from the Climate view: notifications reach this device."));
    }
}
