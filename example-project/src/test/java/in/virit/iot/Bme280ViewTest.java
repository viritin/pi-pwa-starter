package in.virit.iot;

import org.vaadin.firitin.components.SecondaryText;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import in.virit.iot.pihelpers.SimulationBanner;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.vaadin.svgvis.SvgSparkLine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The climate card: gauge, secondary lines and curves for the selectable period. */
@QuarkusTest
class Bme280ViewTest extends ViewTest {

    @Test
    void drawsTheSensorCardWithHistory() {
        navigate(Bme280View.class);
        assertTrue(find(SimulationBanner.class).first().isVisible());
        assertEquals("Simulation · BME280", paragraph("bme280-status"));
        var cards = find(Card.class).all().stream().filter(c -> !(c instanceof SimulationBanner)).toList();
        assertEquals(4, cards.size(), "sensor, details, sharing and wiring cards");
        assertEquals("BME280", cards.get(0).getTitleAsText());
        assertEquals("Humidity and pressure", cards.get(1).getTitleAsText());
        assertTrue(find(SecondaryText.class).withTextContaining("Humidity ").exists());
        assertTrue(find(SecondaryText.class).withTextContaining("Pressure ").exists());
        assertEquals(3, find(SvgSparkLine.class).all().stream().filter(SvgSparkLine::isVisible).count(),
                "temperature, humidity and pressure curves from the seeded history");

        test(find(RadioButtonGroup.class).withLabel("History").first()).selectItem("24 h");
        assertEquals(3, find(SvgSparkLine.class).all().stream().filter(SvgSparkLine::isVisible).count());
    }

    /** The sharing card starts idle; with lookup off in tests, it points at the manual form. */
    @Test
    void offersToShareWithHomeAssistant() {
        navigate(Bme280View.class);
        assertEquals("Not sharing", paragraph("ha-status"));
        assertEquals("Automatic lookup is switched off; enter the broker below.", paragraph("ha-found"));
        assertTrue(findTextField().withLabel("Broker host").exists());
        assertTrue(findTextField().withLabel("Device id").component().getValue().length() > 0,
                "a device id is generated from the host");
        assertEquals(1883, findIntegerField().withLabel("Port").component().getValue());
    }
}
