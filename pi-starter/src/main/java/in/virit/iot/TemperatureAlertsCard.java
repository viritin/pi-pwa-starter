package in.virit.iot;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.WebStorage;
import com.vaadin.flow.component.textfield.NumberField;
import in.virit.iot.pihelpers.auth.CurrentUser;
import in.virit.iot.push.PushSubscriptionStore;
import in.virit.iot.push.TemperatureAlerts;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * Min/max temperature alerts for this browser, delivered with Web Push. The
 * browser's own subscription is the source of truth for the switch: the server's
 * record follows it. Push needs a secure context and, on iPhone, iPad and Safari
 * on macOS, the app installed to the home screen or Dock.
 */
class TemperatureAlertsCard extends Card {

    private static final String CLIENT_ID_KEY = "pi-starter.push-client-id";

    /** Asked before any Web Push call: those throw where the browser has no push support. */
    private static final String SUPPORT_JS = """
            if (!window.isSecureContext) return 'NEEDS_SECURE_CONNECTION';
            if (!('serviceWorker' in navigator) || !('PushManager' in window)) return 'NOT_OFFERED';
            return navigator.serviceWorker.getRegistration()
                .then(r => !r ? 'NO_SERVICE_WORKER' : r.pushManager ? 'AVAILABLE' : 'NOT_OFFERED');
            """;

    private final TemperatureAlerts alerts;
    private final Checkbox enabled = new Checkbox("Notifications on this device");
    private final LimitField min = new LimitField("Min °C");
    private final LimitField max = new LimitField("Max °C");
    private final Button test = new Button("Send a test notification");
    private final Paragraph hint = new Paragraph(
            "Get a notification on this device when the temperature leaves the range you set.");
    private String clientId;

    TemperatureAlertsCard(TemperatureAlerts alerts) {
        this.alerts = alerts;
        setTitle("Temperature alerts");
        setWidthFull();
        hint.setId("push-hint");
        enabled.setEnabled(false);
        test.setEnabled(false);
        enabled.addValueChangeListener(e -> {
            if (e.isFromClient()) {
                if (e.getValue()) {
                    subscribe(e.getSource().getUI().orElseThrow());
                } else {
                    unsubscribe(e.getSource().getUI().orElseThrow());
                }
            }
        });
        min.addValueChangeListener(e -> saveLimits());
        max.addValueChangeListener(e -> saveLimits());
        test.addClickListener(e -> {
            alerts.sendTest(clientId);
            Notification.show("Test notification sent.");
        });
        add(new Body());
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        UI ui = event.getUI();
        WebStorage.getItem(ui, WebStorage.Storage.LOCAL_STORAGE, CLIENT_ID_KEY, stored -> {
            clientId = stored;
            if (!PushSubscriptionStore.isValidClientId(clientId)) {
                clientId = UUID.randomUUID().toString();
                WebStorage.setItem(ui, WebStorage.Storage.LOCAL_STORAGE, CLIENT_ID_KEY, clientId);
            }
            alerts.find(clientId).ifPresent(s -> {
                min.setValue(s.min());
                max.setValue(s.max());
            });
            ui.getPage().executeJs(SUPPORT_JS).then(String.class, status -> {
                if ("AVAILABLE".equals(status)) {
                    alerts.subscriptionExists(ui, this::showSubscribed);
                } else {
                    explainUnavailable(status);
                }
            });
        });
    }

    /** Follows the browser: its subscription may have gone, or the server lost its record. */
    private void showSubscribed(boolean subscribed) {
        if (!subscribed) {
            alerts.unsubscribe(clientId);
        }
        boolean known = subscribed && alerts.find(clientId).isPresent();
        enabled.setEnabled(true);
        enabled.setValue(known);
        test.setEnabled(known);
    }

    private void subscribe(UI ui) {
        ui.getPage().executeJs("return window.Vaadin.Flow.webPush.subscribe($0)", alerts.publicKey())
                .then(json -> {
                    store(json);
                    test.setEnabled(true);
                    hint.setText("Notifications are on. Set a min and/or max to get alerts.");
                }, error -> {
                    enabled.setValue(false);
                    hint.setText(explain(error));
                    Notification.show(hint.getText(), 8000, Notification.Position.MIDDLE);
                });
    }

    private void store(JsonNode json) {
        JsonNode keys = json.path("keys");
        alerts.subscribe(clientId, json.path("endpoint").asString(), keys.path("p256dh").asString(),
                keys.path("auth").asString(), min.getValue(), max.getValue(),
                CurrentUser.get().map(CurrentUser.AuthUser::username).orElse(null));
    }

    private void unsubscribe(UI ui) {
        // Forget it on the server even if the browser call fails
        alerts.unsubscribe(clientId);
        test.setEnabled(false);
        ui.getPage().executeJs("return window.Vaadin.Flow.webPush.unsubscribe()");
    }

    private void saveLimits() {
        if (clientId != null) {
            alerts.setLimits(clientId, min.getValue(), max.getValue());
        }
    }

    private void explainUnavailable(String status) {
        enabled.setEnabled(false);
        hint.setText(switch (status) {
            case "NEEDS_SECURE_CONNECTION" -> "Notifications need a secure connection: HTTPS, "
                    + "or localhost while developing. See HTTPS.md.";
            case "NO_SERVICE_WORKER" -> "Notifications need the app's service worker, which has not "
                    + "started yet. Reload the page.";
            default -> "This browser does not offer notifications here. On iPhone and iPad, add the app "
                    + "to the home screen first; with Safari on a Mac, add it to the Dock.";
        });
    }

    private static String explain(String error) {
        if (error != null && error.contains("blocked notifications")) {
            return "Notifications are blocked for this site. Allow them in the browser's settings.";
        }
        if (error != null && error.contains("push service not available")) {
            return "This browser has no push service here (for example a private window).";
        }
        return "Could not turn notifications on: " + error;
    }

    /** A temperature limit; empty means no limit on that side. */
    static class LimitField extends NumberField {
        LimitField(String label) {
            super(label);
            setStep(0.5);
            setClearButtonVisible(true);
            setWidth("8rem");
        }
    }

    /** The card's contents, spaced like the rest of the view. */
    class Body extends VerticalLayout {
        Body() {
            setPadding(false);
            add(hint, enabled, new Limits(), test);
        }
    }

    /** Min and max side by side, wrapping on a narrow screen. */
    class Limits extends FlexLayout {
        Limits() {
            setFlexWrap(FlexWrap.WRAP);
            setAlignItems(Alignment.BASELINE);
            getStyle().setGap("1rem");
            add(min, max);
        }
    }
}
