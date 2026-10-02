package in.virit.iot.pihelpers;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.shared.Registration;
import in.virit.iot.pihelpers.BleScanService.Device;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Nearby Bluetooth LE devices as a live list: name, address, signal, who made
 * it and what it advertises. Scanning runs while the panel is on screen. Rows
 * keep their place while their numbers update; a button re-sorts by signal on
 * demand, so the list can be read while it is live. No route or menu entry; the
 * application adds those.
 */
@StyleSheet("styles/pi-helpers-ble.css")
public class BlePanel extends VerticalLayout {

    private final BleScanService service;
    private final ScanningSwitch scanning = new ScanningSwitch();
    private final FilterField filter = new FilterField();
    private final DeviceList list = new DeviceList();
    private final Paragraph status = new Paragraph();
    private final SetupHint setup = PiSetup.bluetooth();
    private final SimulationBanner simulation = new SimulationBanner(
            "These devices are invented; no radio is listening.");
    private Registration listener;

    public BlePanel(BleScanService service) {
        this.service = service;
        addClassName("ble-panel");
        status.setId("ble-status");
        add(new H1("Bluetooth LE"),
                new Paragraph("Everything advertising nearby. Nothing is connected to; this only listens, so a tag "
                        + "or a phone shows up as soon as it is switched on. Rows keep their place while their "
                        + "signal updates; Sort by signal puts the strongest first. Devices that fall silent drop "
                        + "off the list after a minute."),
                simulation, new Toolbar(), status, list, setup);
        simulation.setVisible(Simulated.is(service));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();
        listener = service.addListener(() -> ui.access(() -> {
            if (isAttached()) {
                refresh();
            }
        }));
        service.watch();
        refresh();
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (listener != null) {
            listener.remove();
            listener = null;
        }
        service.unwatch();
    }

    private void refresh() {
        scanning.setValue(service.isScanning());
        var devices = service.devices().stream().filter(this::matches).toList();
        list.update(devices);
        status.setText(service.status());
        if (service.hasProblem()) {
            setup.setOpened(true);
        }
    }

    private boolean matches(Device device) {
        String needle = filter.needle();
        if (needle.isEmpty()) {
            return true;
        }
        return device.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || device.address().toLowerCase(Locale.ROOT).contains(needle)
                || device.companies().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(needle));
    }

    class Toolbar extends HorizontalLayout {
        Toolbar() {
            add(scanning, filter, new Button("Sort by signal", e -> list.sortBySignal()) {{
                setId("ble-sort");
                addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            }});
            setAlignItems(Alignment.BASELINE);
            addClassName("ble-toolbar");
        }
    }

    /** Starts and stops the scan; set from the service's state on every refresh. */
    class ScanningSwitch extends Switch {
        ScanningSwitch() {
            super("Scanning");
            addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    if (e.getValue()) {
                        service.start();
                    } else {
                        service.stop();
                    }
                    refresh();
                }
            });
        }
    }

    class FilterField extends TextField {
        FilterField() {
            setId("ble-filter");
            setPlaceholder("Filter by name, address or maker");
            setClearButtonVisible(true);
            setValueChangeMode(ValueChangeMode.LAZY);
            addValueChangeListener(e -> refresh());
            setWidth("18em");
        }

        /** The filter text, trimmed and lower case; empty for none. */
        String needle() {
            return getValue() == null ? "" : getValue().trim().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One row per device. Rows are updated in place and keep their position: a
     * new device joins at the end, and the order only changes on request. The
     * first fill arrives strongest first, so a fresh list starts in signal order.
     */
    class DeviceList extends Div {
        private final Map<String, DeviceRow> rows = new HashMap<>();
        private final Paragraph empty = new Paragraph("Nothing heard yet.");

        DeviceList() {
            addClassName("ble-list");
            add(empty);
        }

        void update(List<Device> devices) {
            var seen = new HashSet<String>();
            for (var device : devices) {
                seen.add(device.address());
                rows.computeIfAbsent(device.address(), address -> {
                    var created = new DeviceRow();
                    add(created);
                    return created;
                }).update(device);
            }
            rows.keySet().removeIf(address -> {
                if (!seen.contains(address)) {
                    remove(rows.get(address));
                    return true;
                }
                return false;
            });
            empty.setVisible(devices.isEmpty());
        }

        /** Puts the rows in signal order once, strongest first; they then stay put again. */
        void sortBySignal() {
            var ordered = rows.values().stream()
                    .sorted(Comparator.comparing((DeviceRow row) -> row.last.rssi() == null ? Integer.MIN_VALUE : row.last.rssi())
                            .reversed().thenComparing(row -> row.last.address()))
                    .toList();
            int index = 1; // the "Nothing heard yet" paragraph stays first
            for (var row : ordered) {
                getElement().insertChild(index++, row.getElement());
            }
        }
    }

    static class DeviceRow extends Div {
        private Device last;
        private final Part name = new Part("ble-name");
        private final Part address = new Part("ble-address");
        private final Part rssi = new Part("ble-rssi");
        private final Part bar = new Part("ble-bar");
        private final Part connected = new Part("ble-badge");
        private final Part companies = new Part("ble-detail");
        private final Part services = new Part("ble-detail");
        private final Part payload = new Part("ble-detail", "ble-payload");
        private final Part seen = new Part("ble-detail");

        DeviceRow() {
            addClassName("ble-device");
            connected.setText("connected");
            add(new Group("ble-head", name, connected, address),
                    new Group("ble-signal", bar, rssi),
                    new Group("ble-details", companies, services, payload, seen));
        }

        void update(Device device) {
            last = device;
            name.setText(device.displayName());
            name.setClassName("ble-unnamed", device.name() == null || device.name().isBlank());
            address.setText(device.address());
            connected.setVisible(device.connected());
            rssi.setText(device.rssi() == null ? "–" : device.rssi() + " dBm"
                    + (device.txPower() != null ? " · tx " + device.txPower() : ""));
            bar.getElement().setAttribute("data-level", String.valueOf(level(device.rssi())));
            companies.setText(device.companies().isEmpty() ? "No manufacturer data" : String.join(", ", device.companies()));
            services.setText(device.serviceUuids().isEmpty() ? "" : services(device.serviceUuids()));
            services.setVisible(!device.serviceUuids().isEmpty());
            payload.setText(device.payload() == null ? "" : device.payload());
            payload.setVisible(device.payload() != null);
            long ago = Duration.between(device.lastSeen(), Instant.now()).toSeconds();
            seen.setText(ago < 3 ? "just now" : ago + " s ago");
        }

        private static String services(List<String> uuids) {
            var first = uuids.get(0);
            String label = first.length() == 36 && first.endsWith("-0000-1000-8000-00805f9b34fb")
                    ? "0x" + first.substring(4, 8).toUpperCase(Locale.ROOT) : first;
            return uuids.size() == 1 ? "Service " + label : "Services " + label + " +" + (uuids.size() - 1);
        }

        /** A piece of the row, styled by its class names. */
        static class Part extends Span {
            Part(String... classNames) {
                addClassNames(classNames);
            }
        }

        /** Pieces of the row laid out together. */
        static class Group extends Div {
            Group(String className, Component... parts) {
                super(parts);
                addClassName(className);
            }
        }

        /** 0–4 bars: below -90 is barely there, above -60 is next to you. */
        static int level(Integer rssi) {
            if (rssi == null) return 0;
            if (rssi >= -60) return 4;
            if (rssi >= -70) return 3;
            if (rssi >= -80) return 2;
            if (rssi >= -90) return 1;
            return 0;
        }
    }
}
