package in.virit.iot.diagnostics;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
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
    private Registration registration;
    private Span status;
    private boolean replaying;

    @Inject
    public IncidentOverlay(IncidentReporter reporter) {
        this.reporter = reporter;
        addClassName("incident-overlay");
        status = new Span();
        var details = new Button("Details", event -> openDetails());
        details.addClassName("incident-details");
        add(status, details);
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        replaying = true;
        registration = reporter.addListener(incident -> getUI().ifPresent(ui -> ui.access(() -> {
            refresh();
            if (!replaying) showToast();
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
        setVisible(count > 0);
        status.setText(count + (count == 1 ? " server error" : " server errors"));
    }

    private void showToast() {
        var notification = new Notification();
        notification.setPosition(Notification.Position.TOP_END);
        notification.setDuration(8_000);
        notification.add(new Span("A server error was captured."),
                new Button("Details", event -> {
                    notification.close();
                    openDetails();
                }));
        notification.open();
    }

    private void openDetails() {
        var dialog = new Dialog();
        dialog.setHeaderTitle("Server errors");
        var content = new VerticalLayout();
        content.setPadding(false);
        for (var incident : reporter.recent()) {
            var title = new Span(TIME.format(incident.time()) + " · " + incident.source() + " · " + incident.thread());
            title.getStyle().setFontWeight("600");
            var text = new TextArea();
            text.setReadOnly(true);
            text.setWidthFull();
            text.setValue(incident.displayText());
            content.add(title, text);
        }
        dialog.add(content);
        var close = new Button("Close", event -> dialog.close());
        dialog.getFooter().add(close);
        dialog.open();
    }
}
