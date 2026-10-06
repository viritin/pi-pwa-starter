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
class ClimateViewTest extends ViewTest {

    @Test
    void drawsTheSensorCardWithHistory() {
        navigate(ClimateView.class);
        assertTrue(find(SimulationBanner.class).first().isVisible());
        assertEquals("Simulation · BME280", paragraph("bme280-status"));
        var cards = find(Card.class).all().stream().filter(c -> !(c instanceof SimulationBanner)).toList();
        assertEquals(3, cards.size(), "sensor, details and wiring cards");
        assertEquals("BME280", cards.get(0).getTitleAsText());
        assertEquals("Humidity and pressure", cards.get(1).getTitleAsText());
        assertTrue(find(SecondaryText.class).withTextContaining("Humidity ").exists());
        assertTrue(find(SecondaryText.class).withTextContaining("Pressure ").exists());
        assertEquals(2, find(SvgSparkLine.class).all().stream().filter(SvgSparkLine::isVisible).count(),
                "temperature, and humidity with pressure in one chart, from the seeded history");

        test(find(RadioButtonGroup.class).withLabel("History").first()).selectItem("24 h");
        assertEquals(2, find(SvgSparkLine.class).all().stream().filter(SvgSparkLine::isVisible).count());
    }
}
