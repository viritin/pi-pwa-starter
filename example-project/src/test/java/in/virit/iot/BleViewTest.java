package in.virit.iot;

import com.vaadin.flow.component.checkbox.Switch;
import com.vaadin.flow.component.html.Div;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scanner lists what the (simulated) radio hears and filters it. */
@QuarkusTest
class BleViewTest extends ViewTest {

    @Test
    void listsAndFiltersNearbyDevices() {
        navigate(BleView.class);
        awaitPush(() -> paragraph("ble-status").contains("in range"), "the first scan result");
        assertEquals("Simulation · scanning · 5 in range", paragraph("ble-status"));
        var rows = find(Div.class).withClassName("ble-device").all();
        assertEquals(5, rows.size());
        assertTrue(textOf(rows.get(0)).contains("Ruuvi 229F"), "strongest signal first");

        test(findTextField().withId("ble-filter").component()).setValue("apple");
        rows = find(Div.class).withClassName("ble-device").all();
        assertEquals(1, rows.size());
        assertTrue(textOf(rows.get(0)).contains("0x004C Apple"));

        toggle(find(Switch.class).withLabel("Scanning").first());
        assertEquals("Not scanning", paragraph("ble-status"));
    }
}
