package in.virit.iot;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.I2cPanel;
import in.virit.iot.pihelpers.I2cService;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Application-specific route and navigation for the reusable I²C panel. */
@Route(value = "i2c", layout = TopLayout.class)
@MenuItem(title = "I2C", icon = VaadinIcon.SITEMAP, order = 2, parent = ProtoTools.class)
@PageTitle("I2C | Pi Starter")
public class I2cView extends I2cPanel {

    @Inject
    public I2cView(I2cService i2cService) {
        super(i2cService);
        addClassName("page");
    }
}
