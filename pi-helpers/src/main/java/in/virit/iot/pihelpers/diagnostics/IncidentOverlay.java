package in.virit.iot.pihelpers.diagnostics;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.shared.Registration;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Small global corner notice mounted by the application layout. */
@Dependent
public class IncidentOverlay extends Div {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final IncidentReporter reporter;
    private final Span status = new Span();
    private Registration registration;
    private boolean replaying;

    @Inject
    public IncidentOverlay(IncidentReporter reporter) {
        this.reporter = reporter;
        // fixed in the corner, above the page, red so it is not mistaken for content
        getStyle()
                .setPosition(Style.Position.FIXED).setZIndex(20).setRight("1rem").setBottom("1rem")
                .setAlignItems(Style.AlignItems.CENTER).setGap(".75rem").setPadding(".7rem .85rem")
                .setColor("#fff").setBackground("#a32929").setBorderRadius(".75rem")
                .setBoxShadow("0 .4rem 1.5rem #0004");
        add(status, new DetailsButton());
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        replaying = true;
        registration = reporter.addListener(incident -> getUI().ifPresent(ui -> ui.access(() -> {
            refresh();
            if (!replaying) new Toast().open();
        })));
        refresh();
        replaying = false;
    }

    @Override
    protected void onDetach(DetachEvent event) {
        if (registration != null) registration.remove();
        registration = null;
        super.onDetach(event);
    }

    private void refresh() {
        var count = reporter.pendingCount();
        // display rather than setVisible: an inline display would win over the hidden attribute
        getStyle().setDisplay(count > 0 ? Style.Display.FLEX : Style.Display.NONE);
        status.setText(count + (count == 1 ? " server error" : " server errors"));
    }

    /** The notice's own button, outlined in white on the red. */
    class DetailsButton extends Button {
        DetailsButton() {
            super("Details", event -> new DetailsDialog().open());
            getStyle().setColor("#fff").setBackground("transparent").setBorder("1px solid #fff8");
        }
    }

    /** A brief heads-up when a new error is captured while the user is here. */
    class Toast extends Notification {
        Toast() {
            setPosition(Position.TOP_END);
            setDuration(8_000);
            add(new Span("A server error was captured."), new Button("Details", event -> {
                close();
                new DetailsDialog().open();
            }));
        }
    }

    /** The recent errors in full, each under a line saying when and where it happened. */
    class DetailsDialog extends Dialog {
        DetailsDialog() {
            setHeaderTitle("Server errors");
            add(new IncidentList());
            getFooter().add(new Button("Close", event -> close()));
        }

        class IncidentList extends VerticalLayout {
            IncidentList() {
                setPadding(false);
                for (var incident : reporter.recent()) {
                    add(new IncidentTitle(incident), new IncidentText(incident));
                }
            }
        }

        static class IncidentTitle extends Span {
            IncidentTitle(Incident incident) {
                super(TIME.format(incident.time()) + " · " + incident.source() + " · " + incident.thread());
                getStyle().setFontWeight(600);
            }
        }

        static class IncidentText extends TextArea {
            IncidentText(Incident incident) {
                setReadOnly(true);
                setWidthFull();
                setValue(incident.displayText());
            }
        }
    }
}
