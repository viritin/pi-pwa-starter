package in.virit.iot;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.Component;

@Route("")
@RouteAlias("about")
@Menu(title = "About", icon = "vaadin:info-circle", order = 1)
@PageTitle("About | Pi Starter")
public class AboutView extends VerticalLayout {

    public AboutView() {
        add(new Hero());
        add(new Section("Make yourself at home",
                new Paragraph("Connect a sensor, automate a light or keep an eye on your home. "
                        + "This application gives your project a home on your phone and desktop.")));
        add(new Section("Examples and building blocks",
                new Paragraph(new Name("Blink a LED"), new Span(" is the smallest complete feature: a view, a service "
                        + "that owns one output through the shared Pi4J context, a development simulation and error handling. "
                        + "Copy it for a relay, a buzzer or a button.")),
                new Paragraph(new Name("Climate"), new Span(" reads a BME280 sensor from application start, keeps a day "
                        + "of history and draws it with a gauge and sparklines. Copy it for anything that "
                        + "produces a stream of numbers.")),
                new Paragraph(new Name("Passkey sign-in"), new Span(" is built in and off by default: set "
                        + "starter.auth.enabled=true for passwordless access with passkeys, and invite more people "
                        + "from Users. See AUTH.md.")),
                new Paragraph(new Name("Home Assistant"), new Span(" integration is kept out of the starter to keep it "
                        + "lean. For an example of sending data to another system, the "
                        + "example/home-assistant-integration-via-mqtt branch publishes the Climate readings over MQTT, "
                        + "picked up by Home Assistant's discovery.")),
                new Paragraph(new Name("Web Push"), new Span(" notifications live on a branch of their own too: "
                        + "example/web-push-notifications adds min and max temperature alerts to the Climate view, "
                        + "delivered to a phone or desktop without any third-party service."))));
        add(new Section("Tools for the workbench",
                new Paragraph(new Name("System"), new Span(" shows how the device is doing and which interfaces are "
                        + "enabled, and can reboot or shut it down. ")),
                new Paragraph(new Name("Proto Tools"), new Span(" checks the wiring before you write code: drive and read "
                        + "GPIOs, scan the I²C bus and poke registers, position a servo with PWM, "
                        + "read 1-Wire probes and see which Bluetooth LE devices are around.")),
                new Paragraph("They are meant for prototyping on a trusted network. In a finished application you "
                        + "will most likely remove them or put them behind a login (passkey sign-in above): Proto "
                        + "Tools come from pi-helpers and are switched off with starter.proto-tools.enabled=false, "
                        + "System is SystemView in this project.")));
        add(new Section("Keep it close",
                new Paragraph("Add this app to your home screen using your browser’s install or share menu. "
                        + "Live views need a connection to this device.")));
    }

    /** Logo next to the headline; on a narrow screen the headline wraps below it. */
    static class Hero extends FlexLayout {
        Hero() {
            setWidthFull();
            setFlexWrap(FlexWrap.WRAP);
            setAlignItems(Alignment.CENTER);
            getStyle().setGap("clamp(1rem, 3vw, 2rem)");
            add(new Logo(), new Heading());
        }

        static class Logo extends Image {
            Logo() {
                super("icons/logo.svg", "Pi Starter logo");
                // intrinsic size, so the page does not jump while the SVG loads
                getElement().setAttribute("width", "128").setAttribute("height", "128");
                getStyle()
                        .setWidth("clamp(6rem, 20vw, 8rem)")
                        .setHeight("auto")
                        .setFlexShrink("0")
                        .setBorderRadius("22%");
            }
        }

        static class Heading extends Div {
            Heading() {
                getStyle().setDisplay(Style.Display.GRID).setGap(".75rem").setMinWidth("0").setFlexBasis("20rem")
                        .setFlexGrow("1");
                add(new Eyebrow("YOUR NEXT WEEKEND PROJECT"), new H1("Small device. Big possibilities."), new Tagline());
            }

            /** The small spaced-out caps line above the headline. */
            static class Eyebrow extends Span {
                Eyebrow(String text) {
                    super(text);
                    getStyle().setFontSize(".75rem").setFontWeight(Style.FontWeight.BOLD)
                            .set("letter-spacing", ".12em"); // no Style method for it
                }
            }

            static class Tagline extends Paragraph {
                Tagline() {
                    super("A starting point for the things you want to measure, automate and make your own.");
                    getStyle().setMargin("0");
                }
            }
        }
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
