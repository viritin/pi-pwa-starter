package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.webshare.ShareContent;
import com.vaadin.flow.component.webshare.WebShare;

import java.time.Duration;

/**
 * A button that mints a single-use registration link for a {@link User} and hands
 * it over the OS share sheet (Web Share API), with copy-to-clipboard as the
 * fallback where sharing is unavailable. All of the invite plumbing — token
 * creation, resolving the absolute URL, the share/copy dialog — is hidden here so
 * callers just drop it next to a user.
 */
public class InviteButton extends Button {

    /** How long a freshly minted invitation stays valid. */
    public static final Duration DEFAULT_VALIDITY = Duration.ofDays(7);

    public InviteButton(User user, UserStore users, String appName) {
        super("Invite link", VaadinIcon.SHARE.create());
        addClickListener(e -> offer(user, users, DEFAULT_VALIDITY, appName));
    }

    /**
     * Creates an invitation for the user and opens the share/copy dialog. Exposed
     * statically so a "create user" flow can offer the link right away, without a
     * button of its own.
     */
    public static void offer(User user, UserStore users, Duration validity, String appName) {
        InviteToken invite = users.createInvite(user.username(), user.displayName(), user.roles(), validity);
        // The absolute URL needs the browser's location; fetch it, then show the dialog.
        UI.getCurrent().getPage().fetchCurrentURL(url -> {
            String link = url.getProtocol() + "://" + url.getAuthority() + "/register?token=" + invite.token();
            new InviteDialog(user.username(), link, validity, appName).open();
        });
    }

    /** The link, a share button (armed for the user gesture) and a copy fallback. */
    private static class InviteDialog extends Dialog {
        InviteDialog(String username, String link, Duration validity, String appName) {
            setHeaderTitle("Registration link for " + username);
            var field = new TextField();
            field.setValue(link);
            field.setReadOnly(true);
            field.setWidthFull();

            // Web Share opens the native share sheet and must fire within the click's
            // user-gesture window, so the button is armed here at construction.
            var share = new Button("Share", VaadinIcon.SHARE.create());
            share.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            WebShare.onClick(share).share(ShareContent.create()
                    .title(appName + " registration")
                    .text("You've been invited to " + appName + ". Set up your passkey:")
                    .url(link));

            var copy = new Button("Copy", e ->
                    field.getElement().executeJs("navigator.clipboard && navigator.clipboard.writeText($0)", link));

            var content = new VerticalLayout(
                    new Paragraph("Send this single-use link to " + username
                            + ". It expires in " + validity.toDays() + " days."),
                    field);
            content.setPadding(false);
            add(content);
            getFooter().add(copy, share, new Button("Close", e -> close()));
        }
    }
}
