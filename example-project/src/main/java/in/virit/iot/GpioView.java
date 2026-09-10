package in.virit.iot;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.GpioPanel;
import in.virit.iot.pihelpers.GpioService;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Application-specific route and navigation for the reusable GPIO panel. */
@Route(value = "gpio", layout = TopLayout.class)
@MenuItem(title = "GPIO", icon = VaadinIcon.CONNECT, order = 1, parent = ProtoTools.class)
@PageTitle("GPIO | Pi Starter")
public class GpioView extends GpioPanel {

    @Inject
    public GpioView(GpioService gpioService) {
        super(gpioService);
        addClassName("page");
    }
}
