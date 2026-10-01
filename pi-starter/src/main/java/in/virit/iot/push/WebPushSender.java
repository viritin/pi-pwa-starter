package in.virit.iot.push;

import com.vaadin.flow.server.webpush.WebPush;
import com.vaadin.flow.server.webpush.WebPushException;
import com.vaadin.flow.server.webpush.WebPushKeys;
import com.vaadin.flow.server.webpush.WebPushMessage;
import com.vaadin.flow.server.webpush.WebPushSubscription;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Delivers notifications through the browsers' push services, on a thread of its
 * own so a slow push service never holds up sensor sampling. A subscription the
 * push service no longer knows (HTTP 404/410: unsubscribed, app removed) is
 * forgotten.
 */
@ApplicationScoped
public class WebPushSender {

    private static final Logger LOG = Logger.getLogger(WebPushSender.class);

    @Inject
    VapidKeys keys;
    @Inject
    PushSubscriptionStore store;

    private WebPush webPush;
    private ExecutorService executor;

    @PostConstruct
    void init() {
        webPush = new WebPush(keys.publicKey(), keys.privateKey(), keys.subject());
        executor = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "web-push-sender");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Lets a notification already on its way finish before the application stops. */
    @PreDestroy
    void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /** Vaadin's Web Push helper, for the browser-side calls. */
    WebPush webPush() {
        return webPush;
    }

    String publicKey() {
        return keys.publicKey();
    }

    void send(AlertSubscription subscription, String title, String body) {
        executor.execute(() -> deliver(subscription, title, body));
    }

    private void deliver(AlertSubscription subscription, String title, String body) {
        try {
            webPush.sendNotification(
                    new WebPushSubscription(subscription.endpoint(),
                            new WebPushKeys(subscription.p256dh(), subscription.auth())),
                    new WebPushMessage(title, body));
        } catch (WebPushException e) {
            // Vaadin reports a gone subscription only through the message text
            if (e.getMessage() != null && e.getMessage().contains("404 or 410")) {
                LOG.infof("Push subscription of %s is gone; forgetting it", subscription.clientId());
                store.delete(subscription.clientId());
            } else {
                LOG.warnf(e, "Could not deliver a push notification to %s", subscription.clientId());
            }
        }
    }
}
