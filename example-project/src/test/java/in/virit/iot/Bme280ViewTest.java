package in.virit.iot;

import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
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
        assertEquals("Simulation · BME280", paragraph("bme280-status"));
        var cards = find(Card.class).all();
        assertEquals(2, cards.size());
        assertEquals("BME280", cards.get(0).getTitleAsText());
        assertTrue(findSpan().withTextContaining("Humidity ").exists());
        assertTrue(findSpan().withTextContaining("Pressure ").exists());
        assertEquals(3, find(SvgSparkLine.class).all().stream().filter(SvgSparkLine::isVisible).count(),
                "temperature, humidity and pressure curves from the seeded history");

        test(find(RadioButtonGroup.class).withLabel("History").first()).selectItem("24 h");
        assertEquals(3, find(SvgSparkLine.class).all().stream().filter(SvgSparkLine::isVisible).count());
    }
}
