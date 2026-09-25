package in.virit.iot;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.shared.Registration;
import in.virit.iot.bme280.ClimatePublisher;
import in.virit.iot.pihelpers.HomeAssistantFinder;
import in.virit.iot.pihelpers.HomeAssistantFinder.Instance;
import in.virit.iot.pihelpers.MqttPublisher;
import in.virit.iot.pihelpers.MqttSettings;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.Optional;

/**
 * "Share with Home Assistant": finds the instance on the network, connects to
 * its MQTT broker with one tap, asks for a login only if the broker insists,
 * and shows what is being published. A manual form covers brokers elsewhere.
 */
public class ClimateSharingCard extends Card {

    private static final Logger LOG = Logger.getLogger(ClimateSharingCard.class);

    private final ClimatePublisher publisher;
    private final HomeAssistantFinder finder;
    private final Paragraph found = new Paragraph();
    private final Paragraph status = new Paragraph();
    private final Button connect = new Button("Connect");
    private final Button stop = new Button("Stop sharing");
    private final Button remove = new Button("Remove from Home Assistant");
    private final ManualForm manual = new ManualForm();
    /** Null until the network lookup has answered; then what it found, if anything. */
    private Optional<Instance> lookup;
    private boolean lookupStarted;
    private Registration listener;
    private CredentialsDialog credentials;

    public ClimateSharingCard(ClimatePublisher publisher, HomeAssistantFinder finder) {
        this.publisher = publisher;
        this.finder = finder;
        // A card of its own below the readings, not one of their row, so not a ClimateCard
        setTitle("Share with Home Assistant");
        addThemeVariants(CardVariant.OUTLINED);
        setWidthFull();
        getStyle().setMaxWidth("44rem");
        found.setId("ha-found");
        status.setId("ha-status");
        connect.setId("ha-connect");
        connect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        connect.addClickListener(e -> connectToFound());
        stop.addClickListener(e -> perform(publisher::stopSharing));
        remove.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        remove.addClickListener(e -> new ConfirmDialog("Remove from Home Assistant?",
                "The device and its three sensors disappear from Home Assistant and sharing stops. "
                        + "Connecting again brings them back.",
                "Remove", confirm -> perform(publisher::removeFromHomeAssistant)) {{
            setCancelable(true);
            open();
        }});
        add(new Paragraph("Publishes the readings over MQTT with Home Assistant's discovery messages, so the "
                + "device and its sensors appear in Home Assistant by themselves. Nothing to configure "
                + "there beyond the Mosquitto broker."));
        if (publisher.isConfiguredExternally()) {
            var settings = publisher.settings();
            var configured = new Paragraph("Configured in application.properties (starter.mqtt.*): broker "
                    + settings.host() + ":" + settings.port() + ", device id " + settings.deviceId()
                    + (settings.enabled() ? "" : ", publishing disabled") + ". Change it there.");
            add(configured, status);
        } else {
            var otherBroker = new Details("Use another broker", manual);
            otherBroker.setWidthFull(); // so the form sees the card's width and can use two columns
            // Filled when opened rather than on every status change, so nothing overwrites typing
            otherBroker.addOpenedChangeListener(e -> manual.fill(publisher.settings()));
            add(found, new HorizontalLayout(connect, stop, remove), status, otherBroker);
            manual.fill(publisher.settings());
        }
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        UI ui = attachEvent.getUI();
        listener = publisher.addListener(() -> ui.access(() -> {
            if (isAttached()) {
                refresh();
            }
        }));
        if (!lookupStarted && !publisher.isConfiguredExternally()) {
            lookupStarted = true;
            lookUp(ui);
        }
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }

    private void lookUp(UI ui) {
        if (!finder.isEnabled()) {
            found.setText("Automatic lookup is switched off; enter the broker below.");
            return;
        }
        found.setText("Looking for Home Assistant on this network…");
        var thread = new Thread(() -> {
            var instances = finder.find(Duration.ofSeconds(3));
            ui.access(() -> {
                lookup = instances.stream().findFirst();
                refresh();
            });
        }, "home-assistant-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectToFound() {
        found().ifPresent(target -> perform(() -> publisher.start(publisher.settings()
                .withBroker(target.host(), target.hasBroker() ? target.mqttPort() : HomeAssistantFinder.MQTT_PORT))));
    }

    private Optional<Instance> found() {
        return lookup == null ? Optional.empty() : lookup;
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.warn("Sharing operation failed", failure);
            Notification.show(failure.getMessage() != null ? failure.getMessage() : "Operation failed.",
                    5000, Notification.Position.MIDDLE);
        }
        refresh();
    }

    private void refresh() {
        var settings = publisher.settings();
        boolean sharing = settings.enabled();
        var target = found();
        if (lookup != null) {
            found.setText(target.map(ClimateSharingCard::describe).orElse("No Home Assistant announced on this "
                    + "network. It may be on another subnet or running without host networking; use the form below."));
        }
        connect.setVisible(!sharing && target.isPresent());
        connect.setEnabled(target.filter(t -> t.hasBroker() || t.port() > 0).isPresent());
        stop.setVisible(sharing);
        remove.setVisible(settings.hasBroker());
        status.setText(publisher.status());
        var state = publisher.state();
        if (sharing && state == MqttPublisher.State.UNAUTHORIZED && (credentials == null || !credentials.isOpened())) {
            credentials = new CredentialsDialog(settings);
            credentials.open();
        }
    }

    private static String describe(Instance target) {
        if (target.port() <= 0) {
            return "MQTT broker “" + target.name() + "” found at " + target.host() + ":" + target.mqttPort();
        }
        return "Home Assistant “" + target.name() + "” found at " + target.host()
                + (target.hasBroker() ? " · MQTT broker answers on port " + target.mqttPort()
                : " · no broker on port 1883, is the Mosquitto add-on installed?");
    }

    /** Only when the broker asks: the Mosquitto add-on wants a Home Assistant login. */
    class CredentialsDialog extends Dialog {
        CredentialsDialog(MqttSettings settings) {
            setHeaderTitle("The broker needs a login");
            var username = new TextField("Username", settings.username(), "");
            var password = new PasswordField("Password");
            password.setValue(settings.password() == null ? "" : settings.password());
            add(new Paragraph("Use a Home Assistant user, or a login configured in the Mosquitto add-on. "
                    + "It is stored on this device for the next start."), new FormLayout(username, password));
            var connect = new Button("Connect", e -> {
                close();
                perform(() -> publisher.start(settings.withCredentials(username.getValue(), password.getValue())));
            });
            connect.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            getFooter().add(new Button("Cancel", e -> {
                close();
                perform(publisher::stopSharing);
            }), connect);
        }
    }

    /** Everything editable, for a broker mDNS did not find or settings other than the defaults. */
    class ManualForm extends FormLayout {
        private final TextField host = new TextField("Broker host");
        private final IntegerField port = new IntegerField("Port");
        private final TextField username = new TextField("Username");
        private final PasswordField password = new PasswordField("Password");
        private final TextField deviceId = new TextField("Device id");
        private final TextField topicPrefix = new TextField("Topic prefix");
        private final IntegerField interval = new IntegerField("Interval (s)");

        ManualForm() {
            setAutoResponsive(true);
            setAutoRows(true); // fields flow into the columns instead of one per row
            setMaxColumns(2);
            setExpandFields(true);
            host.setPlaceholder("homeassistant.local or 192.168.1.10");
            port.setMin(1);
            port.setMax(65535);
            deviceId.setHelperText("Part of every topic; keep it once Home Assistant knows it");
            interval.setMin(5);
            interval.setMax(3600);
            var apply = new Button("Connect with these settings", e -> perform(() -> publisher.start(read())));
            apply.setId("ha-connect-manual");
            // Two columns where there is room, one on a phone: auto-responsive decides
            add(host, port, username, password, deviceId, topicPrefix, interval);
            add(apply, 2);
            add(new Paragraph("Topics: <prefix>/<device id>/climate/state and …/status; "
                    + "discovery under homeassistant/sensor/…"), 2);
        }

        void fill(MqttSettings settings) {
            host.setValue(settings.host() == null ? "" : settings.host());
            port.setValue(settings.port());
            username.setValue(settings.username() == null ? "" : settings.username());
            password.setValue(settings.password() == null ? "" : settings.password());
            deviceId.setValue(settings.deviceId() == null ? "" : settings.deviceId());
            topicPrefix.setValue(settings.topicPrefix());
            interval.setValue((int) settings.interval().toSeconds());
        }

        MqttSettings read() {
            var current = publisher.settings();
            return new MqttSettings(current.enabled(), host.getValue().trim(),
                    port.getValue() == null ? HomeAssistantFinder.MQTT_PORT : port.getValue(),
                    username.getValue().trim(), password.getValue(),
                    deviceId.getValue().isBlank() ? current.deviceId() : deviceId.getValue().trim(),
                    topicPrefix.getValue().isBlank() ? current.topicPrefix() : topicPrefix.getValue().trim(),
                    current.discoveryPrefix(),
                    interval.getValue() == null ? MqttSettings.DEFAULT_INTERVAL : Duration.ofSeconds(interval.getValue()));
        }
    }
}
