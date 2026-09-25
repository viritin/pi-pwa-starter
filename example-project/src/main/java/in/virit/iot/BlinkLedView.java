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
import com.vaadin.flow.component.card.Card;
import in.virit.iot.led.LedService;
import in.virit.iot.pihelpers.PiSetup;
import in.virit.iot.pihelpers.SetupHint;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * An example view to build your own application on. It shows the pieces a
 * typical Pi feature needs: a route with a menu entry, a small CDI service
 * ({@link LedService}) that owns the hardware through the shared
 * {@code Pi4JContext}, form fields bound to that service, a simulated mode for
 * development without a Pi, and error handling that restores the last known
 * good state. Copy it, rename it and replace the LED with your sensor or
 * actuator. The generic screens under "Proto Tools" are for checking wiring
 * and are not meant as a starting point for application code.
 */
@Route("blink-led")
@Menu(title = "Blink a LED", icon = "vaadin:lightbulb", order = 3)
@PageTitle("Blink a LED | Pi Starter")
public class BlinkLedView extends VerticalLayout {
    private static final Logger LOG = Logger.getLogger(BlinkLedView.class);
    private final LedService service;
    private final GpioField gpio = new GpioField();
    private final Switch led = new Switch("LED on");
    private final Paragraph status = new Paragraph();
    private final SetupHint setup = PiSetup.gpio();
    private boolean editingGpio;
    private Registration poll;
    private int previousPollInterval;

    @Inject
    public BlinkLedView(LedService service) {
        this.service = service;
        addClassName("page");
        gpio.addFocusListener(e -> editingGpio = true);
        gpio.addBlurListener(e -> editingGpio = false);
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

        add(new H1("Blink a LED"),
                new Paragraph("Choose a GPIO and switch your LED on or off."),
                new Paragraph("Connect the GPIO through a current-limiting resistor to the LED, "
                        + "and the LED’s cathode to GND. HIGH is 3.3 V; LOW is 0 V."),
                new LedControls(), setup);
        refresh();
    }

    /** The pin, the switch and what the LED is doing, stacked in one card. */
    class LedControls extends Card {
        LedControls() {
            var stack = new VerticalLayout(gpio, led, status);
            stack.setPadding(false);
            add(stack);
        }
    }

    /** A BCM GPIO number, 0–27, with the hint that it is not the header pin. */
    static class GpioField extends IntegerField {
        GpioField() {
            super("GPIO number (BCM)");
            setMin(0);
            setMax(27);
            setStepButtonsVisible(true);
            setHelperText("BCM number, not the physical header pin. GPIO 26 = header pin 37.");
            setErrorMessage("Enter a whole BCM GPIO number from 0 to 27.");
        }
    }

    private void perform(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException | IllegalArgumentException explained) {
            // Our own services say what is wrong in words meant for the user
            LOG.warn("LED operation failed", explained);
            Notification.show(explained.getMessage(), 8000, Notification.Position.MIDDLE);
            setup.setOpened(true);
        } catch (RuntimeException failure) {
            LOG.warn("LED operation failed", failure);
            Notification.show("Could not change the LED: " + failure.getMessage()
                    + ". Check the GPIO selection, device and permissions.", 8000, Notification.Position.MIDDLE);
            setup.setOpened(true);
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
