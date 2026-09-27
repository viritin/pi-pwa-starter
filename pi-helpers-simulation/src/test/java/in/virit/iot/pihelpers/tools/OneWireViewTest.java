package in.virit.iot.pihelpers.tools;

import in.virit.iot.pihelpers.testing.ViewTest;

import com.vaadin.flow.component.html.Div;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Readings arrive from the panel's own thread and are pushed to the page. */
@QuarkusTest
class OneWireViewTest extends ViewTest {

    @Test
    void showsThePushedReadings() {
        navigate(OneWireView.class);
        awaitPush(() -> paragraph("onewire-status").contains("2 devices"), "the first 1-Wire refresh");
        var badges = find(Div.class).withClassName("stat").all();
        assertEquals(2, badges.size());
        assertTrue(textOf(badges.get(0)).contains("DS18B20 · 28-000005e2fdc3"));
        assertTrue(textOf(badges.get(0)).contains("°C"));
    }
}
