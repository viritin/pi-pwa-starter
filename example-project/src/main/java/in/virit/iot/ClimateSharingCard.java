package in.virit.iot;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
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
import java.util.List;

/**
 * "Share with Home Assistant": finds the instance on the network, connects to
 * its MQTT broker with one tap, asks for a login only if the broker insists,
 * and shows what is being published. A manual form covers brokers elsewhere.
 */
public class ClimateSharingCard extends ClimateCard {

    private static final Logger LOG = Logger.getLogger(ClimateSharingCard.class);

    private final ClimatePublisher publisher;
    private final HomeAssistantFinder finder;
    private final Paragraph found = new Paragraph();
    private final Paragraph status = new Paragraph();
    private final Button connect = new Button("Connect");
    private final Button stop = new Button("Stop sharing");
    private final Button remove = new Button("Remove from Home Assistant");
    private final ManualForm manual = new ManualForm();
    private volatile Instance instance;
    private boolean lookedUp;
    private boolean lookupDone;
    private Registration listener;
    private CredentialsDialog credentials;

    public ClimateSharingCard(ClimatePublisher publisher, HomeAssistantFinder finder) {
        this.publisher = publisher;
        this.finder = finder;
        super("Share with Home Assistant");
        getStyle().setMaxWidth("44rem"); // more text than the reading cards
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
        var actions = new FlexLayout(connect, stop, remove);
        actions.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        actions.getStyle().setGap("var(--vaadin-gap-s, .5rem)");
        add(new Paragraph("Publishes the readings over MQTT with Home Assistant's discovery messages, so the "
                + "device and its sensors appear in Home Assistant by themselves. Nothing to configure "
                + "there beyond the Mosquitto broker."));
        if (publisher.isConfiguredExternally()) {
            var settings = publisher.settings();
            var configured = new Paragraph("Configured in application.properties (starter.mqtt.*): broker "
                    + settings.host() + ":" + settings.port() + ", device id " + settings.deviceId()
                    + (settings.enabled() ? "" : ", publishing disabled") + ". Change it there.");
            configured.setId("ha-found");
            add(configured, status);
        } else {
            add(found, actions, status, new Details("Use another broker", manual));
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
        if (!lookedUp && !publisher.isConfiguredExternally()) {
            lookedUp = true;
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
            List<Instance> instances = finder.find(Duration.ofSeconds(3));
            ui.access(() -> {
                instance = instances.isEmpty() ? null : instances.get(0);
                lookupDone = true;
                refresh();
            });
        }, "home-assistant-lookup");
        thread.setDaemon(true);
        thread.start();
    }

    private void connectToFound() {
        var target = instance;
        if (target == null) {
            return;
        }
        var settings = publisher.settings().withBroker(target.host(), target.hasBroker() ? target.mqttPort() : HomeAssistantFinder.MQTT_PORT);
        perform(() -> publisher.start(settings));
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
        var state = publisher.state();
        boolean sharing = settings.enabled();
        var target = instance;
        if (lookupDone && finder.isEnabled()) {
            if (target == null) {
                found.setText("No Home Assistant announced on this network. It may be on another subnet "
                        + "or running without host networking; use the form below.");
            } else if (target.port() > 0) {
                found.setText("Home Assistant “" + target.name() + "” found at " + target.host()
                        + (target.hasBroker() ? " · MQTT broker answers on port " + target.mqttPort()
                        : " · no broker on port 1883, is the Mosquitto add-on installed?"));
            } else {
                found.setText("MQTT broker “" + target.name() + "” found at " + target.host() + ":" + target.mqttPort());
            }
        }
        connect.setVisible(!sharing && target != null);
        connect.setEnabled(target != null && (target.hasBroker() || target.port() > 0));
        stop.setVisible(sharing);
        remove.setVisible(settings.hasBroker());
        status.setText(publisher.status());
        manual.show(settings);
        if (sharing && state == MqttPublisher.State.UNAUTHORIZED && (credentials == null || !credentials.isOpened())) {
            credentials = new CredentialsDialog(settings);
            credentials.open();
        }
    }

    /** Only when the broker asks: the Mosquitto add-on wants a Home Assistant login. */
    class CredentialsDialog extends Dialog {
        CredentialsDialog(MqttSettings settings) {
            setHeaderTitle("The broker needs a login");
            var username = new TextField("Username", settings.username(), "");
            var password = new PasswordField("Password");
            password.setValue(settings.password() == null ? "" : settings.password());
            var form = new VerticalLayout(new Paragraph("Use a Home Assistant user, or a login configured in the "
                    + "Mosquitto add-on. It is stored on this device for the next start."), username, password);
            form.setPadding(false);
            add(form);
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
    class ManualForm extends VerticalLayout {
        private final TextField host = new TextField("Broker host");
        private final IntegerField port = new IntegerField("Port");
        private final TextField username = new TextField("Username");
        private final PasswordField password = new PasswordField("Password");
        private final TextField deviceId = new TextField("Device id");
        private final TextField topicPrefix = new TextField("Topic prefix");
        private final IntegerField interval = new IntegerField("Interval (s)");
        private boolean editing;

        ManualForm() {
            setPadding(false);
            host.setPlaceholder("homeassistant.local or 192.168.1.10");
            host.setWidth("16em");
            port.setWidth("6em");
            port.setMin(1);
            port.setMax(65535);
            deviceId.setHelperText("Part of every topic; keep it once Home Assistant knows it");
            interval.setMin(5);
            interval.setMax(3600);
            interval.setWidth("7em");
            for (var field : List.of(host, username, password, deviceId, topicPrefix)) {
                field.addFocusListener(e -> editing = true);
                field.addBlurListener(e -> editing = false);
            }
            var apply = new Button("Connect with these settings", e -> perform(() -> publisher.start(read())));
            apply.setId("ha-connect-manual");
            var fields = new FlexLayout(host, port, username, password, deviceId, topicPrefix, interval);
            fields.setFlexWrap(FlexLayout.FlexWrap.WRAP);
            fields.setAlignItems(Alignment.BASELINE);
            fields.getStyle().setGap(".75rem");
            add(fields, apply, new Paragraph("Topics: <prefix>/<device id>/climate/state and …/status; "
                    + "discovery under homeassistant/sensor/…"));
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

        void show(MqttSettings settings) {
            if (editing) {
                return;
            }
            host.setValue(settings.host() == null ? "" : settings.host());
            port.setValue(settings.port());
            username.setValue(settings.username() == null ? "" : settings.username());
            password.setValue(settings.password() == null ? "" : settings.password());
            deviceId.setValue(settings.deviceId() == null ? "" : settings.deviceId());
            topicPrefix.setValue(settings.topicPrefix());
            interval.setValue((int) settings.interval().toSeconds());
        }
    }
}
