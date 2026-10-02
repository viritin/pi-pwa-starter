package in.virit.iot.pihelpers;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.details.DetailsVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AnchorTarget;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

/**
 * Setup instructions the user can act on directly: a sentence of context,
 * blocks of shell commands or config lines with a copy button, and links to
 * the documentation that explains the rest. Folded into a Details so a panel
 * whose hardware already works stays uncluttered; panels open it when the
 * hardware they need is missing. {@link PiSetup} has the ready-made recipes.
 */
@StyleSheet("styles/pi-helpers-setup.css")
public class SetupHint extends Details {

    private LinkRow links;

    public SetupHint(String summary) {
        setSummaryText(summary);
        addClassName("setup-hint");
        addThemeVariants(DetailsVariant.FILLED);
        // Aura indents the content to the summary text and gives it almost no padding on
        // the right or below; its no-padding variant steps aside so the stylesheet can
        // give the content one even inset instead.
        addThemeName("no-padding");
    }

    /** A sentence or two of context. */
    public SetupHint text(String text) {
        add(new Paragraph(text));
        return this;
    }

    /** Shell commands or file lines, one per argument, with a button that copies them all. */
    public SetupHint commands(String caption, String... lines) {
        add(new CommandBlock(caption, lines));
        return this;
    }

    /** A link to documentation, opened in a new tab. Several links share one "Read more" row. */
    public SetupHint link(String text, String url) {
        if (links == null) {
            links = new LinkRow();
            add(links);
        }
        links.add(new DocLink(text, url));
        return this;
    }

    /** Copyable command block: caption, preformatted lines and a copy button. */
    static class CommandBlock extends Div {

        // The Clipboard API only exists in secure contexts; a Pi on plain http gets the textarea fallback.
        private static final String COPY_JS = """
                const text = $0;
                if (navigator.clipboard && window.isSecureContext) {
                    return navigator.clipboard.writeText(text).then(() => true, () => false);
                }
                const area = document.createElement('textarea');
                area.value = text;
                area.setAttribute('readonly', '');
                area.style.position = 'fixed';
                area.style.opacity = '0';
                document.body.appendChild(area);
                area.select();
                let ok = false;
                try { ok = document.execCommand('copy'); } catch (e) { }
                area.remove();
                return ok;
                """;

        CommandBlock(String caption, String... lines) {
            addClassName("setup-commands");
            String text = String.join("\n", lines);
            if (caption != null && !caption.isBlank()) {
                add(new Caption(caption));
            }
            add(new Pre(text), new CopyButton(text));
        }

        static class Caption extends Span {
            Caption(String text) {
                super(text);
                addClassName("setup-caption");
            }
        }

        static class CopyButton extends Button {
            CopyButton(String text) {
                super(VaadinIcon.COPY.create());
                addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
                setAriaLabel("Copy");
                setTooltipText("Copy");
                addClickListener(e -> getUI().ifPresent(ui -> ui.getPage().executeJs(COPY_JS, text)
                        .then(Boolean.class, ok -> Notification.show(Boolean.TRUE.equals(ok)
                                ? "Copied" : "Could not copy; select the text and copy it by hand"))));
            }
        }
    }

    /** The "Read more" row the documentation links gather in. */
    static class LinkRow extends Div {
        LinkRow() {
            super(new Span("Read more:"));
            addClassName("setup-links");
        }
    }

    /** External documentation link that leaves the app in its own tab. */
    static class DocLink extends Anchor {
        DocLink(String text, String url) {
            super(url, text);
            setTarget(AnchorTarget.BLANK);
            getElement().setAttribute("rel", "noopener");
        }
    }
}
