package in.virit.iot.pihelpers;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.exceptions.Mqtt3ConnAckException;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAckReturnCode;
import com.vaadin.flow.shared.Registration;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One MQTT connection for the application, built on the HiveMQ client with its
 * automatic reconnect. Connecting happens on a background thread and the result
 * is reported through {@link #state()} and listeners, so a view can show
 * "connecting", "unauthorized" or "connected" without blocking. Publishing is a
 * short blocking call; the client queues while reconnecting. Listeners are
 * called on the publisher's own thread, so they may publish.
 */
@ApplicationScoped
public class MqttPublisher {

    private static final Logger LOG = Logger.getLogger(MqttPublisher.class);

    public enum State { DISCONNECTED, CONNECTING, CONNECTED, UNAUTHORIZED, ERROR }

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "mqtt-publisher");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Mqtt3BlockingClient client;
    /** Set when the current client is being taken down, so its reconnects stop. */
    private volatile AtomicBoolean stopping = new AtomicBoolean();
    private volatile State state = State.DISCONNECTED;
    private volatile String status = "Not connected";
    private volatile MqttSettings settings;

    public State state() {
        return state;
    }

    public String status() {
        return status;
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public MqttSettings settings() {
        return settings;
    }

    /** Called on the publisher thread whenever the state changes; wrap UI work in ui.access. */
    public Registration addListener(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    /**
     * Connects in the background. {@code willTopic}/{@code willPayload} is what the
     * broker publishes when this client disappears without saying goodbye.
     */
    public synchronized void connect(MqttSettings settings, String willTopic, String willPayload) {
        disconnectQuietly();
        this.settings = settings;
        set(State.CONNECTING, "Connecting to " + settings.host() + ":" + settings.port());
        var stop = new AtomicBoolean();
        stopping = stop;
        var built = Mqtt3Client.builder()
                .identifier(settings.topicPrefix() + "-" + settings.deviceId())
                .serverHost(settings.host())
                .serverPort(settings.port())
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(context -> set(State.CONNECTED, "Connected to " + settings.host() + ":" + settings.port()))
                .addDisconnectedListener(context -> {
                    // The client retries even the first connect; a refused login must not loop,
                    // and a client that is being replaced must not come back.
                    if (stop.get()) {
                        context.getReconnector().reconnect(false);
                        return;
                    }
                    if (context.getCause() instanceof Mqtt3ConnAckException refused) {
                        var code = refused.getMqttMessage().getReturnCode();
                        boolean login = code == Mqtt3ConnAckReturnCode.NOT_AUTHORIZED
                                || code == Mqtt3ConnAckReturnCode.BAD_USER_NAME_OR_PASSWORD;
                        set(login ? State.UNAUTHORIZED : State.ERROR, "Broker refused the connection: " + code);
                        if (login) {
                            context.getReconnector().reconnect(false);
                        }
                    } else if (state == State.CONNECTED) {
                        set(State.CONNECTING, "Connection lost, reconnecting: " + describe(context.getCause()));
                    } else if (state == State.CONNECTING) {
                        set(State.ERROR, "Could not connect to " + settings.host() + ":" + settings.port()
                                + ": " + describe(context.getCause()) + " (retrying)");
                    }
                })
                .willPublish().topic(willTopic).payload(bytes(willPayload)).qos(MqttQos.AT_LEAST_ONCE).retain(true)
                .applyWillPublish()
                .buildBlocking();
        client = built;
        executor.execute(() -> {
            try {
                var connect = built.connectWith().cleanSession(true).keepAlive(30);
                if (settings.hasCredentials()) {
                    connect = connect.simpleAuth().username(settings.username()).password(bytes(settings.password())).applySimpleAuth();
                }
                connect.send();
            } catch (Mqtt3ConnAckException refused) {
                // Already reported by the disconnected listener; just make sure the client is gone
                disconnectQuietly();
            } catch (RuntimeException e) {
                if (!stop.get()) {
                    set(State.ERROR, "Could not connect to " + settings.host() + ":" + settings.port() + ": " + describe(e));
                }
                disconnectQuietly();
            }
        });
    }

    public synchronized void disconnect() {
        disconnectQuietly();
        set(State.DISCONNECTED, "Not connected");
    }

    /** Publishes with QoS 1; an empty payload on a retained topic clears it. */
    public void publish(String topic, String payload, boolean retain) {
        var current = client;
        if (current == null || state != State.CONNECTED) {
            throw new IllegalStateException("Not connected to an MQTT broker");
        }
        current.publishWith().topic(topic).payload(bytes(payload)).qos(MqttQos.AT_LEAST_ONCE).retain(retain).send();
        LOG.debugf("MQTT %s%s: %s", topic, retain ? " (retained)" : "", payload);
    }

    private void disconnectQuietly() {
        stopping.set(true);
        var current = client;
        client = null;
        if (current != null) {
            try {
                // Async: also ends a reconnect loop, which the blocking call would refuse
                current.toAsync().disconnect();
            } catch (RuntimeException e) {
                LOG.debugf(e, "MQTT disconnect failed");
            }
        }
    }

    private void set(State newState, String message) {
        state = newState;
        status = message;
        LOG.infof("MQTT: %s", message);
        // Listeners publish in response to "connected"; that must never run on the
        // client's own network thread, where a blocking send would wait for itself.
        executor.execute(() -> {
            for (var listener : listeners) {
                try {
                    listener.run();
                } catch (RuntimeException e) {
                    LOG.debug("MQTT listener failed", e);
                }
            }
        });
    }

    private static String describe(Throwable cause) {
        if (cause == null) {
            return "unknown reason";
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static byte[] bytes(String text) {
        return (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
    }

    @PreDestroy
    void shutdown() {
        disconnectQuietly();
        executor.shutdownNow();
    }
}
