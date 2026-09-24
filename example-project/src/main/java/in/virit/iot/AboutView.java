package in.virit.iot;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.Component;

@Route(value = "", layout = TopLayout.class)
@RouteAlias(value = "about", layout = TopLayout.class)
@Menu(title = "About", icon = "vaadin:info-circle", order = 1)
@PageTitle("About | Pi Starter")
public class AboutView extends VerticalLayout {

    public AboutView() {
        addClassName("page");
        var eyebrow = new Span("YOUR NEXT WEEKEND PROJECT");
        eyebrow.addClassName("eyebrow");
        var logo = new Image("icons/pi-starter.svg", "Pi Starter logo");
        logo.addClassName("about-logo");
        logo.getElement().setAttribute("width", "128").setAttribute("height", "128");
        var heading = new Div(eyebrow, new H1("Small device. Big possibilities."),
                new Paragraph("A starting point for the things you want to measure, automate and make your own."));
        heading.addClassName("about-heading");
        var hero = new Div(logo, heading);
        hero.addClassName("about-hero");
        add(hero);
        add(new Section("Make yourself at home",
                new Paragraph("Connect a sensor, automate a light or keep an eye on your home. "
                        + "This application gives your project a home on your phone and desktop.")));
        add(new Section("Two examples to build on",
                new Paragraph(new Name("Blink a LED"), new Span(" is the smallest complete feature: a view, a service "
                        + "that owns one output through the shared Pi4J context, a simulated mode and error handling. "
                        + "Copy it for a relay, a buzzer or a button.")),
                new Paragraph(new Name("Climate"), new Span(" reads a BME280 sensor from application start, keeps a day "
                        + "of history and draws it with a gauge and sparklines. Copy it for anything that "
                        + "produces a stream of numbers."))));
        add(new Section("Tools for the workbench",
                new Paragraph(new Name("System"), new Span(" shows how the device is doing and which interfaces are "
                        + "enabled, and can reboot or shut it down. ")),
                new Paragraph(new Name("Proto Tools"), new Span(" checks the wiring before you write code: drive and read "
                        + "GPIOs, scan the I²C bus and poke registers, position a servo with PWM, "
                        + "read 1-Wire probes and see which Bluetooth LE devices are around.")),
                new Paragraph("They are meant for prototyping on a trusted network. In a finished application you "
                        + "will most likely remove them from the menu, or put them behind a login: delete the "
                        + "views in this project or drop the pi-helpers dependency.")));
        add(new Section("Keep it close",
                new Paragraph("Add this app to your home screen using your browser’s install or share menu. "
                        + "Live views need a connection to this device.")));
    }

    /** One titled block of the page. */
    static class Section extends Card {
        Section(String title, Component... content) {
            setTitle(title);
            setWidthFull();
            add(content);
        }
    }

    /** A view's name inside running text, set apart so a paragraph can be scanned by it. */
    static class Name extends Span {
        Name(String text) {
            super(text);
            getStyle().setFontWeight(Style.FontWeight.BOLD);
        }
    }
}
