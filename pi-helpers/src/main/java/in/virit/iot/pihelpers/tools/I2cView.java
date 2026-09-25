package in.virit.iot.pihelpers.tools;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.I2cPanel;
import in.virit.iot.pihelpers.I2cService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Proto Tools route for the I²C bus scanner and register tools; registered by {@link ProtoToolsRegistrar}. */
// Not registered at startup: ProtoToolsRegistrar adds it when enabled. Being a @Route still makes
// Vaadin bundle the components it uses, which a route registered only at runtime would miss.
@Route(registerAtStartup = false)
@MenuItem(title = "I2C", icon = VaadinIcon.SITEMAP, order = 2, parent = ProtoTools.class)
@PageTitle("I2C")
@Dependent
@Unremovable
public class I2cView extends I2cPanel {

    @Inject
    public I2cView(I2cService i2cService) {
        super(i2cService);
        addClassName("page");
    }
}
