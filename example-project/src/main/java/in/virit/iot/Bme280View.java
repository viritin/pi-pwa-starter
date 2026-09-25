package in.virit.iot;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouterLink;
import in.virit.iot.pihelpers.tools.I2cView;
import com.vaadin.flow.shared.Registration;
import in.virit.TemperatureGauge;
import in.virit.iot.pihelpers.HomeAssistantFinder;
import in.virit.iot.pihelpers.PiSetup;
import in.virit.iot.bme280.Bme280Service;
import in.virit.iot.bme280.ClimatePublisher;
import in.virit.iot.bme280.Bme280Service.Reading;
import jakarta.inject.Inject;
import org.vaadin.svgvis.SvgSparkLine;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The second example view: a sensor that produces a stream of numbers, shown the
 * way ScrewCloud's pi-reader shows its tags. A gauge for the headline value, the
 * other readings as secondary lines, and the recent history as a curve, with the
 * period selectable. Everything is pushed from {@link Bme280Service}; the view
 * only draws. Below the cards, {@link ClimateSharingCard} publishes the same
 * readings to Home Assistant over MQTT, and wiring instructions make the screen
 * useful before the sensor is connected.
 */
@Route
@Menu(title = "Climate", icon = "vaadin:cloud-o", order = 4)
@PageTitle("Climate | Pi Starter")
public class Bme280View extends VerticalLayout {

    enum Range {
        MINUTES_15("15 min", Duration.ofMinutes(15)),
        HOUR("1 h", Duration.ofHours(1)),
        HOURS_6("6 h", Duration.ofHours(6)),
        DAY("24 h", Duration.ofHours(24));

        final String label;
        final Duration duration;

        Range(String label, Duration duration) {
            this.label = label;
            this.duration = duration;
        }
    }

    private final Bme280Service service;
    private final RadioButtonGroup<Range> range = new RadioButtonGroup<>("History", List.of(Range.values())){{
        addThemeVariants(RadioGroupVariant.AURA_HORIZONTAL);
    }};
    private final SensorCard card = new SensorCard();
    private final DetailsCard details = new DetailsCard();
    private final Paragraph status = new Paragraph();
    private Registration listener;

    @Inject
    public Bme280View(Bme280Service service, ClimatePublisher publisher, HomeAssistantFinder finder) {
        this.service = service;
        addClassName("page");
        status.setId("bme280-status");
        range.setValue(Range.HOUR);
        range.setItemLabelGenerator(r -> r.label);
        range.addValueChangeListener(e -> refresh());
        var cards = new ClimateCards(card, details);
        add(new H1("Climate"),
                new Paragraph("Temperature, humidity and air pressure from a BME280 on the I²C bus, "
                        + "sampled every few seconds since the application started."),
                range, cards, status, new ClimateSharingCard(publisher, finder), new WiringPanel());
        refresh();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();
        listener = service.addListener(reading -> ui.access(() -> {
            if (isAttached()) {
                refresh();
            }
        }));
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

    private void refresh() {
        var latest = service.latest();
        var history = service.history(range.getValue().duration);
        card.update(latest.orElse(null), history);
        details.update(history);
        status.setText(service.status());
    }

    /** Formats a value or an en dash when there is none; zero is a real temperature. */
    static String format(Double value, String pattern) {
        return value == null ? "–" : String.format(Locale.ROOT, pattern, value);
    }

    /** The headline card: gauge on top, the other numbers under it, temperature history as a curve. */
    class SensorCard extends ClimateCard {
        private final TemperatureGauge gauge = new TemperatureGauge();
        private final Span subtitle = new Span();
        private final SecondaryLine humidity = new SecondaryLine();
        private final SecondaryLine pressure = new SecondaryLine();
        private final SecondaryLine updated = new SecondaryLine();
        private final ClimateSparkLine temperature = new ClimateSparkLine("Temperature °C");

        SensorCard() {
            super("Climate sensor");
            addClassName("climate-gauge-card"); // the gauge's media slot needs ::part styling, see starter.css
            addThemeVariants(CardVariant.COVER_MEDIA);
            setSubtitle(subtitle);
            gauge.setWidthFull();
            setMedia(gauge);
            add(humidity, pressure, updated, temperature);
        }

        void update(Reading latest, List<Reading> history) {
            setTitle(service.model() != null ? service.model() : "Climate sensor");
            subtitle.setText(service.isSimulated() ? "Simulation" : service.status());
            gauge.setTemperature(latest == null ? null : latest.temperature());
            humidity.setText("Humidity " + format(latest == null ? null : latest.humidity(), "%.1f %% RH"));
            humidity.setVisible(latest == null || latest.humidity() != null);
            pressure.setText("Pressure " + format(latest == null ? null : latest.pressure(), "%.1f hPa"));
            updated.setText(latest == null ? "Waiting for the first reading"
                    : "Updated " + ClimateSparkLine.CLOCK_SECONDS.format(latest.at().atZone(ZoneId.systemDefault())));
            temperature.setHistory(history, Reading::temperature);
        }
    }

    /** The two secondary quantities as their own curves; humidity disappears with a BMP280. */
    class DetailsCard extends ClimateCard {
        private final ClimateSparkLine humidity = new ClimateSparkLine("Humidity % RH");
        private final ClimateSparkLine pressure = new ClimateSparkLine("Pressure hPa");

        DetailsCard() {
            super("Humidity and pressure");
            add(humidity, pressure);
        }

        void update(List<Reading> history) {
            humidity.setHistory(history, Reading::humidity);
            pressure.setHistory(history, Reading::pressure);
        }
    }

    /** The readings' cards next to each other, wrapping to one per row on a narrow screen. */
    static class ClimateCards extends FlexLayout {
        ClimateCards(Component... cards) {
            super(cards);
            setWidthFull();
            setFlexWrap(FlexWrap.WRAP);
            setAlignItems(Alignment.START);
            getStyle().setGap("1rem");
        }
    }

    /**
     * A time series that fits on a card. The same sparkline pi-reader draws; both
     * ends of the axis carry their time so a curve of ten minutes cannot pass for a
     * day. Long histories are thinned to what a 400 unit wide curve can show.
     */
    static class ClimateSparkLine extends SvgSparkLine {
        static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
        static final DateTimeFormatter CLOCK_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
        static final DateTimeFormatter CLOCK_WITH_DATE = DateTimeFormatter.ofPattern("MMM d HH:mm", Locale.ROOT);
        private static final int MAX_POINTS = 300;

        ClimateSparkLine(String title) {
            super(400, 100);
            addClassName("climate-sparkline"); // SVG strokes and fills follow the theme, see starter.css
            setWidthFull();
            getStyle().setMarginTop(".5rem");
            setTitle(title);
        }

        void setHistory(List<Reading> history, Function<Reading, Double> value) {
            var measured = new ArrayList<Reading>();
            for (var reading : history) {
                if (value.apply(reading) != null) {
                    measured.add(reading);
                }
            }
            // One point is a dot, not a curve; hide the whole thing instead
            if (measured.size() < 2) {
                setVisible(false);
                return;
            }
            setVisible(true);
            var points = thin(measured);
            setData(points.stream().map(Reading::at).toArray(Instant[]::new),
                    points.stream().mapToDouble(r -> value.apply(r)).toArray());
            var zone = ZoneId.systemDefault();
            Instant first = points.getFirst().at();
            Instant last = points.getLast().at();
            var format = LocalDate.ofInstant(first, zone).equals(LocalDate.ofInstant(last, zone)) ? CLOCK : CLOCK_WITH_DATE;
            setTimeScale(format.format(first.atZone(zone)), format.format(last.atZone(zone)));
            // setData only stores; draw() renders and then drops the data to save session memory
            draw();
        }

        private static List<Reading> thin(List<Reading> readings) {
            if (readings.size() <= MAX_POINTS) {
                return readings;
            }
            var thinned = new ArrayList<Reading>(MAX_POINTS + 1);
            double step = (double) (readings.size() - 1) / (MAX_POINTS - 1);
            for (int i = 0; i < MAX_POINTS; i++) {
                thinned.add(readings.get((int) Math.round(i * step)));
            }
            return thinned;
        }
    }

    /** How to connect the sensor, for the two ways it usually arrives on the desk. */
    class WiringPanel extends Card {
        WiringPanel() {
            setTitle("Connecting the sensor");
            setWidthFull();
            add(intro(),
                    PiSetup.i2c(),
                    new H4("BME280 or BMP280 breakout board"),
                    new UnorderedList(
                            new ListItem("VIN or VCC → 3V3, header pin 1 (never 5 V on a 3.3 V board)"),
                            new ListItem("GND → GND, header pin 6 or 9"),
                            new ListItem("SCL → GPIO3 / SCL1, header pin 5"),
                            new ListItem("SDA → GPIO2 / SDA1, header pin 3"),
                            new ListItem("Leave CSB unconnected or tie it to VCC for I²C. SDO to GND gives address 0x76, "
                                    + "SDO to VCC gives 0x77; this view tries both.")),
                    new H4("Waveshare Pioneer600 expansion board"),
                    new Paragraph("Power the Pi down, push the Pioneer600 onto the 40-pin header and power up; there is "
                            + "nothing to wire. Its sensor is a BMP280 at address 0x76, so this view shows temperature and "
                            + "pressure but no humidity. The same scan also lists the board's other I²C chips: the PCF8574 "
                            + "I/O expander at 0x20, the PCF8591 ADC/DAC at 0x48 and the DS3231 clock at 0x68."),
                    new Paragraph("The chip warms itself slightly and a breakout next to the Pi picks up its heat, so "
                            + "expect a reading a degree or two above room temperature; breathe on it to see the curve move."));
        }

        private Paragraph intro() {
            // The I²C tool exists only when Proto Tools are switched on (starter.proto-tools.enabled)
            Component tool = RouteConfiguration.forApplicationScope().isRouteRegistered(I2cView.class)
                    ? new RouterLink("I²C tool under Proto Tools", I2cView.class)
                    : new Span("I²C tool under Proto Tools, or i2cdetect -y 1 on the Pi,");
            return new Paragraph(new Span("The sensor speaks I²C, which is off on a fresh Pi; the steps below turn it "
                    + "on. A scan with the "), tool, new Span(" should then list the sensor at 0x76 or 0x77."));
        }
    }
}
