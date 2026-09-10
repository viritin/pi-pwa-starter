package in.virit.iot.pihelpers;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Finds Home Assistant instances on the local network. Home Assistant announces
 * itself over mDNS as {@code _home-assistant._tcp}; the Mosquitto add-on does
 * not announce anything, but on Home Assistant OS it listens on the same host
 * on port 1883, which {@link #portOpen} can confirm. Brokers that do announce
 * themselves ({@code _mqtt._tcp}) are reported too.
 * <p>
 * mDNS does not cross routers, so this finds only instances on the same subnet.
 * A Home Assistant container needs host networking for its announcements to
 * reach the network at all.
 */
@ApplicationScoped
public class HomeAssistantFinder {

    private static final Logger LOG = Logger.getLogger(HomeAssistantFinder.class);
    public static final int MQTT_PORT = 1883;
    static final String HOME_ASSISTANT_TYPE = "_home-assistant._tcp.local.";
    static final String MQTT_TYPE = "_mqtt._tcp.local.";

    /**
     * @param name     the instance's location name, e.g. "Home"
     * @param host     IPv4 address
     * @param port     the web UI port, usually 8123
     * @param mqttPort a broker port that answered on the same host, or -1
     */
    public record Instance(String name, String host, int port, String version, int mqttPort) {
        public boolean hasBroker() {
            return mqttPort > 0;
        }
    }

    @ConfigProperty(name = "starter.mdns.enabled", defaultValue = "true")
    boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Blocks for about {@code timeout}, at most twice that when the network is
     * unhelpful (JmDNS probes interfaces before it can ask anything); call from a
     * background thread. Returns what was found by then.
     */
    public List<Instance> find(Duration timeout) {
        if (!enabled) {
            return List.of();
        }
        var executor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "mdns-lookup");
            thread.setDaemon(true);
            return thread;
        });
        try {
            return executor.submit(() -> lookup(timeout)).get(timeout.toMillis() * 2 + 1000, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            LOG.warn("mDNS lookup did not finish in time; is multicast blocked on this network?");
            return List.of();
        } catch (ExecutionException | InterruptedException e) {
            LOG.warnf("mDNS lookup failed: %s", e.getMessage());
            return List.of();
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Instance> lookup(Duration timeout) {
        var found = new ArrayList<Instance>();
        try (JmDNS mdns = JmDNS.create()) {
            for (ServiceInfo info : mdns.list(HOME_ASSISTANT_TYPE, timeout.toMillis())) {
                var addresses = info.getInet4Addresses();
                if (addresses.length == 0) {
                    continue;
                }
                String host = addresses[0].getHostAddress();
                String name = info.getPropertyString("location_name");
                found.add(new Instance(name == null || name.isBlank() ? info.getName() : name, host, info.getPort(),
                        info.getPropertyString("version"),
                        portOpen(host, MQTT_PORT, Duration.ofSeconds(1)) ? MQTT_PORT : -1));
            }
            // A broker announcing itself next to (or instead of) Home Assistant
            for (ServiceInfo info : mdns.list(MQTT_TYPE, Math.min(1000, timeout.toMillis()))) {
                var addresses = info.getInet4Addresses();
                if (addresses.length == 0) {
                    continue;
                }
                String host = addresses[0].getHostAddress();
                boolean known = found.stream().anyMatch(i -> i.host().equals(host));
                if (!known) {
                    found.add(new Instance(info.getName(), host, -1, null, info.getPort()));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOG.warnf("mDNS lookup failed: %s", e.getMessage());
        }
        LOG.infof("Home Assistant lookup found %s", found);
        return found;
    }

    public static boolean portOpen(String host, int port, Duration timeout) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
