package in.virit.iot;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import in.virit.iot.led.LedService;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@Route(value = "blink-led", layout = TopLayout.class)
@Menu(title = "Blink a LED", icon = "vaadin:lightbulb", order = 3)
@PageTitle("Blink a LED | Pi Starter")
public class BlinkLedView extends VerticalLayout {
    private static final Logger LOG = Logger.getLogger(BlinkLedView.class);
    private final LedService service;
    private final IntegerField gpio = new IntegerField("GPIO number (BCM)");
    private final Switch led = new Switch("LED on");
    private final Paragraph status = new Paragraph();
    private boolean editingGpio;
    private Registration poll;
    private int previousPollInterval;

    @Inject
    public BlinkLedView(LedService service) {
        this.service = service;
        addClassName("page");
        gpio.addFocusListener(e -> editingGpio = true);
        gpio.addBlurListener(e -> editingGpio = false);
        gpio.setMin(0);
        gpio.setMax(27);
        gpio.setStepButtonsVisible(true);
        gpio.setHelperText("BCM number, not the physical header pin. GPIO 17 = header pin 11.");
        gpio.setErrorMessage("Enter a whole BCM GPIO number from 0 to 27.");
        status.setId("led-status");

        gpio.addValueChangeListener(event -> {
            if (!event.isFromClient()) return;
            Integer pin = event.getValue();
            if (pin == null || pin < 0 || pin > 27 || gpio.isInvalid()) {
                led.setEnabled(false);
                return;
            }
            perform(() -> service.selectPin(pin));
        });
        led.addValueChangeListener(event -> {
            if (event.isFromClient()) {
                perform(() -> service.setOn(gpio.getValue(), event.getValue()));
            }
        });

        var controls = new VerticalLayout(gpio, led, status);
        controls.addClassName("panel");
        add(new H1("Blink a LED"),
                new Paragraph("Choose a GPIO and switch your LED on or off."),
                new Paragraph("Connect the GPIO through a current-limiting resistor to the LED, "
                        + "and the LED’s cathode to GND. HIGH is 3.3 V; LOW is 0 V."),
                controls);
        refresh();
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            LOG.warn("LED operation failed", failure);
            Notification.show("Could not change the LED. Check the GPIO selection, device and permissions.",
                    5000, Notification.Position.MIDDLE);
        }
        refresh();
    }

    private void refresh() {
        var state = service.state();
        gpio.setValue(state.pin());
        gpio.setEnabled(!state.on());
        gpio.setInvalid(false);
        led.setEnabled(true);
        led.setValue(state.on());
        status.setText((state.simulated() ? "Simulation · " : "")
                + "GPIO " + state.pin() + (state.on() ? " · LED on (HIGH)" : " · LED off"));
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        // Keep multiple browsers in sync with the application's single output.
        previousPollInterval = event.getUI().getPollInterval();
        poll = event.getUI().addPollListener(e -> {
            if (!editingGpio && !gpio.isInvalid() && gpio.getValue() != null) refresh();
        });
        event.getUI().setPollInterval(1000);
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (poll != null) poll.remove();
        event.getUI().setPollInterval(previousPollInterval);
        super.onDetach(event);
    }
}
