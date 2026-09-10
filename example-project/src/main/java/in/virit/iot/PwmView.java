package in.virit.iot;

import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.PwmPanel;
import in.virit.iot.pihelpers.PwmService;
import jakarta.inject.Inject;
import org.vaadin.firitin.appframework.MenuItem;

/** Application-specific route and navigation for the reusable PWM/servo panel. */
@Route(value = "pwm", layout = TopLayout.class)
@MenuItem(title = "PWM", icon = VaadinIcon.SLIDERS, order = 3, parent = ProtoTools.class)
@PageTitle("PWM | Pi Starter")
public class PwmView extends PwmPanel {

    @Inject
    public PwmView(PwmService pwmService) {
        super(pwmService);
        addClassName("page");
    }
}
