package in.virit.iot.pihelpers;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import in.virit.iot.pihelpers.OneWireService.Sensor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Live readings from every 1-Wire device on the bus, refreshed while the
 * panel is visible. No route or menu entry; the application adds those.
 */
@StyleSheet("styles/pi-helpers-onewire.css")
public class OneWirePanel extends VerticalLayout {

    private final OneWireService service;
    private final SensorCard card = new SensorCard();
    private final Paragraph status = new Paragraph();
    private ScheduledExecutorService executor;

    public OneWirePanel(OneWireService service) {
        this.service = service;
        addClassName("onewire-panel");
        status.setId("onewire-status");
        add(new H1("1-Wire sensors"),
                new Paragraph("DS18B20 probes and other 1-Wire devices the kernel has found. Data goes to GPIO4 "
                        + "(pin 7) with a 4.7 kΩ resistor between data and 3V3; several probes share the same wire."),
                card, status);
        if (!service.isBusPresent()) {
            status.setText("No 1-Wire bus. Add dtoverlay=w1-gpio to /boot/firmware/config.txt and reboot.");
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "onewire-refresh");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try {
                // Each probe takes up to 750 ms to convert, so read off the UI lock
                var sensors = service.read();
                ui.access(() -> {
                    if (isAttached() && getUI().orElse(null) == ui) {
                        show(sensors);
                    }
                });
            } catch (Exception ignored) {
                // UI gone or transient read failure; next tick retries
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void show(List<Sensor> sensors) {
        card.update(sensors);
        if (service.isBusPresent()) {
            status.setText((service.isSimulated() ? "Simulation · " : "")
                    + sensors.size() + (sensors.size() == 1 ? " device" : " devices") + " · updated "
                    + java.time.LocalTime.now().withNano(0));
        }
    }

    class SensorCard extends Div {
        private final StatGrid grid = new StatGrid();
        private final Map<String, StatBadge> badges = new LinkedHashMap<>();
        private final Paragraph empty = new Paragraph("No devices yet. Check the wiring and the pull-up resistor; "
                + "new probes appear within a few seconds.");

        SensorCard() {
            addClassName("panel");
            add(new H4("Devices"), grid, empty);
        }

        void update(List<Sensor> sensors) {
            var seen = new java.util.HashSet<String>();
            for (var sensor : sensors) {
                seen.add(sensor.id());
                var badge = badges.computeIfAbsent(sensor.id(), id -> {
                    var created = new StatBadge(sensor.family() + " · " + id);
                    grid.add(created);
                    return created;
                });
                badge.setValue(sensor.celsius() != null ? String.format(java.util.Locale.ROOT, "%.1f °C", sensor.celsius())
                        : sensor.error() != null ? sensor.error() : "present");
            }
            badges.keySet().removeIf(id -> {
                if (!seen.contains(id)) {
                    grid.remove(badges.get(id));
                    return true;
                }
                return false;
            });
            empty.setVisible(sensors.isEmpty());
        }
    }
}
