package in.virit.iot.push;

import in.virit.iot.pihelpers.Json;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Push subscriptions as one JSON file per browser under
 * {@code starter.push.dir}/subscriptions (default {@code ~/.pipwa/push}), next to
 * the passkey data and in the same spirit: no database, a handful of files you can
 * read or delete. Kept in memory too, as alerts read them on every sample.
 */
@ApplicationScoped
public class PushSubscriptionStore {

    private static final Logger LOG = Logger.getLogger(PushSubscriptionStore.class);
    /** The id comes from the browser, so it must not be able to name any other file. */
    private static final Pattern CLIENT_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @ConfigProperty(name = "starter.push.dir", defaultValue = "${user.home}/.pipwa/push")
    String dir;

    private final ConcurrentHashMap<String, AlertSubscription> byClient = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        Path subscriptions = subscriptionsDir();
        if (!Files.isDirectory(subscriptions)) {
            return;
        }
        try (Stream<Path> files = Files.list(subscriptions)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try {
                    var subscription = Json.read(Files.readString(p), AlertSubscription.class);
                    byClient.put(subscription.clientId(), subscription);
                } catch (IOException | RuntimeException e) {
                    LOG.warnf(e, "Skipping unreadable %s", p);
                }
            });
        } catch (IOException e) {
            LOG.warnf(e, "Could not list %s", subscriptions);
        }
    }

    public static boolean isValidClientId(String clientId) {
        return clientId != null && CLIENT_ID.matcher(clientId).matches();
    }

    public Optional<AlertSubscription> find(String clientId) {
        return Optional.ofNullable(byClient.get(clientId));
    }

    public List<AlertSubscription> all() {
        return List.copyOf(byClient.values());
    }

    /**
     * Saves the browser's subscription. A browser has one subscription at a time,
     * and the same endpoint may come back under a new id (localStorage cleared),
     * so an older record with that endpoint is dropped.
     */
    public synchronized void save(AlertSubscription subscription) {
        if (!isValidClientId(subscription.clientId())) {
            throw new IllegalArgumentException("Invalid client id");
        }
        byClient.values().stream()
                .filter(s -> s.endpoint().equals(subscription.endpoint())
                        && !s.clientId().equals(subscription.clientId()))
                .map(AlertSubscription::clientId).toList()
                .forEach(this::delete);
        byClient.put(subscription.clientId(), subscription);
        Path file = file(subscription.clientId());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(subscription));
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file + ": " + e.getMessage(), e);
        }
    }

    public synchronized void delete(String clientId) {
        if (byClient.remove(clientId) != null) {
            try {
                Files.deleteIfExists(file(clientId));
            } catch (IOException e) {
                LOG.warnf(e, "Could not delete the subscription of %s", clientId);
            }
        }
    }

    private Path subscriptionsDir() {
        return Path.of(dir).resolve("subscriptions");
    }

    private Path file(String clientId) {
        return subscriptionsDir().resolve(clientId + ".json");
    }
}
