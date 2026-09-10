package in.virit.iot;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@Route(value = "", layout = TopLayout.class)
@RouteAlias(value = "about", layout = TopLayout.class)
@Menu(title = "About", icon = "vaadin:info-circle", order = 1)
@PageTitle("About | Pi Starter")
public class AboutView extends VerticalLayout {

    public AboutView() {
        addClassName("page");
        var eyebrow = new Span("YOUR NEXT WEEKEND PROJECT");
        eyebrow.addClassName("eyebrow");
        add(eyebrow, new H1("Small device. Big possibilities."),
                new Paragraph("A starting point for the things you want to measure, automate and make your own."));
        var intro = new Div(new H2("Make yourself at home"),
                new Paragraph("Connect a sensor, automate a light or keep an eye on your home. "
                        + "This application gives your project a home on your phone and desktop."),
                new Paragraph("Open System to see how this device is doing. GPIO, I2C, PWM and 1-Wire "
                        + "help you check the wiring before writing a line of code."));
        intro.addClassName("panel");
        var install = new Div(new H2("Keep it close"),
                new Paragraph("Add this app to your home screen using your browser’s install or share menu. "
                        + "Live views need a connection to this device."));
        install.addClassName("panel");
        add(intro, install);
    }
}
