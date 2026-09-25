package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.GpioPanel;
import in.virit.iot.pihelpers.GpioService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Proto Tools route for the GPIO header map; registered by {@link ProtoToolsRegistrar}. */
// Not registered at startup: ProtoToolsRegistrar adds it when enabled. Being a @Route still makes
// Vaadin bundle the components it uses, which a route registered only at runtime would miss.
@Route(registerAtStartup = false)
@MenuItem(title = "GPIO", icon = VaadinIcon.CONNECT, order = 1, parent = ProtoTools.class)
@PageTitle("GPIO")
@Dependent
@Unremovable
public class GpioView extends GpioPanel {

    @Inject
    public GpioView(GpioService gpioService) {
        super(gpioService);
    }
}
