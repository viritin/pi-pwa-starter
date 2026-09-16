package in.virit.iot.pihelpers;

import com.vaadin.flow.component.AttachEvent;
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
    private final Switch scanning = new Switch("Scanning");
    private final TextField filter = new TextField();
    private final DeviceList list = new DeviceList();
    private final Paragraph status = new Paragraph();
    private final SetupHint setup = PiSetup.bluetooth();
    private final SimulationBanner simulation = new SimulationBanner(
            "These devices are invented; no radio is listening.");
    private Registration listener;
    private boolean updating;

    public BlePanel(BleScanService service) {
        this.service = service;
        addClassName("ble-panel");
        status.setId("ble-status");
        filter.setId("ble-filter");
        filter.setPlaceholder("Filter by name, address or maker");
        filter.setClearButtonVisible(true);
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.addValueChangeListener(e -> refresh());
        filter.setWidth("18em");
        scanning.addValueChangeListener(e -> {
            if (!updating && e.isFromClient()) {
                if (e.getValue()) {
                    service.start();
                } else {
                    service.stop();
                }
                refresh();
            }
        });
        var sort = new Button("Sort by signal", e -> list.sortBySignal());
        sort.setId("ble-sort");
        sort.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        var toolbar = new HorizontalLayout(scanning, filter, sort);
        toolbar.setAlignItems(Alignment.BASELINE);
        toolbar.addClassName("ble-toolbar");
        add(new H1("Bluetooth LE"),
                new Paragraph("Everything advertising nearby. Nothing is connected to; this only listens, so a tag "
                        + "or a phone shows up as soon as it is switched on. Rows keep their place while their "
                        + "signal updates; Sort by signal puts the strongest first. Devices that fall silent drop "
                        + "off the list after a minute."),
                simulation, toolbar, status, list, setup);
        simulation.setVisible(service.isSimulated());
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
        updating = true;
        try {
            scanning.setValue(service.isScanning());
        } finally {
            updating = false;
        }
        var devices = service.devices().stream().filter(this::matches).toList();
        list.update(devices);
        status.setText(service.status());
        if (service.hasProblem()) {
            setup.setOpened(true);
        }
    }

    private boolean matches(Device device) {
        String needle = filter.getValue() == null ? "" : filter.getValue().trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return true;
        }
        return device.displayName().toLowerCase(Locale.ROOT).contains(needle)
                || device.address().toLowerCase(Locale.ROOT).contains(needle)
                || device.companies().stream().anyMatch(c -> c.toLowerCase(Locale.ROOT).contains(needle));
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
            var seen = new java.util.HashSet<String>();
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
        private final Span name = new Span();
        private final Span address = new Span();
        private final Span rssi = new Span();
        private final Span bar = new Span();
        private final Span connected = new Span("connected");
        private final Span companies = new Span();
        private final Span services = new Span();
        private final Span payload = new Span();
        private final Span seen = new Span();

        DeviceRow() {
            addClassName("ble-device");
            name.addClassName("ble-name");
            address.addClassName("ble-address");
            rssi.addClassName("ble-rssi");
            bar.addClassName("ble-bar");
            connected.addClassName("ble-badge");
            companies.addClassName("ble-detail");
            services.addClassName("ble-detail");
            payload.addClassNames("ble-detail", "ble-payload");
            seen.addClassName("ble-detail");
            var head = new Div(name, connected, address);
            head.addClassName("ble-head");
            var signal = new Div(bar, rssi);
            signal.addClassName("ble-signal");
            var details = new Div(companies, services, payload, seen);
            details.addClassName("ble-details");
            add(head, signal, details);
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
