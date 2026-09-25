package in.virit.iot;

import in.virit.iot.pihelpers.tools.BleView;
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
        // One simulated device comes and goes on purpose, so four or five are in range.
        assertTrue(paragraph("ble-status").matches("Simulation · scanning · [45] in range"), paragraph("ble-status"));
        var rows = find(Div.class).withClassName("ble-device").all();
        assertTrue(rows.size() == 4 || rows.size() == 5, "rows: " + rows.size());
        assertTrue(textOf(rows.get(0)).contains("Ruuvi 229F"), "a fresh list starts strongest first");

        test(findTextField().withId("ble-filter").component()).setValue("apple");
        rows = find(Div.class).withClassName("ble-device").all();
        assertEquals(1, rows.size());
        assertTrue(textOf(rows.get(0)).contains("0x004C Apple"));

        // Rows that come back after the filter join at the end and stay put until sorted on request
        test(findTextField().withId("ble-filter").component()).setValue("");
        awaitPush(() -> find(Div.class).withClassName("ble-device").all().size() >= 4, "the rows to return");
        rows = find(Div.class).withClassName("ble-device").all();
        assertTrue(textOf(rows.get(0)).contains("0x004C Apple"), "the surviving row keeps its place");
        findButton().withId("ble-sort").click();
        rows = find(Div.class).withClassName("ble-device").all();
        assertTrue(textOf(rows.get(0)).contains("Ruuvi 229F"), "sorted on request, strongest first");

        toggle(find(Switch.class).withLabel("Scanning").first());
        assertEquals("Not scanning", paragraph("ble-status"));
    }
}
