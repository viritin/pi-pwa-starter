package in.virit.iot;

import in.virit.iot.pihelpers.tools.GpioView;
import in.virit.iot.pihelpers.tools.I2cView;
import in.virit.iot.pihelpers.tools.PwmView;
import in.virit.iot.pihelpers.tools.OneWireView;
import in.virit.iot.pihelpers.tools.BleView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H1;
import in.virit.iot.pihelpers.auth.UsersView;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Every route opens inside the layout and announces itself with its heading. */
@QuarkusTest
class NavigationTest extends ViewTest {

    @Test
    void everyViewOpensWithItsHeading() {
        assertHeading(AboutView.class, "Small device. Big possibilities.");
        assertHeading(SystemView.class, "System Monitor");
        assertHeading(BlinkLedView.class, "Blink a LED");
        assertHeading(ClimateView.class, "Climate");
        assertHeading(GpioView.class, "GPIO");
        assertHeading(I2cView.class, "I²C");
        assertHeading(PwmView.class, "PWM & servo");
        assertHeading(OneWireView.class, "1-Wire sensors");
        assertHeading(BleView.class, "Bluetooth LE");
    }

    @Test
    void theAboutAliasOpensTheFrontPage() {
        var view = navigate("about", AboutView.class);
        assertInstanceOf(AboutView.class, view);
        assertEquals(view, getCurrentView());
    }

    /** Without starter.auth.enabled there are no users to manage, so the Users route is diverted. */
    @Test
    void usersIsUnavailableWhileAuthIsOff() {
        UI.getCurrent().navigate(UsersView.class);
        assertInstanceOf(AboutView.class, getCurrentView());
    }

    private void assertHeading(Class<? extends com.vaadin.flow.component.Component> view, String heading) {
        navigate(view);
        assertInstanceOf(view, getCurrentView());
        assertEquals(heading, findInView(H1.class).first().getText(), "heading of " + view.getSimpleName());
    }
}
