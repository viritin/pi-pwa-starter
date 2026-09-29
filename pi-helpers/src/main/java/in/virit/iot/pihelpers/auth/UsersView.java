package in.virit.iot.pihelpers.auth;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import in.virit.iot.pihelpers.AppInfo;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Administration of passkey users: who exists, handing out registration links for
 * new people, and removing accounts. Reachable by admins once sign-in is on; it
 * is also visible (with a hint) while sign-in is still off, so an operator can
 * prepare accounts before flipping {@code starter.auth.enabled}.
 */
@Route("users")
@Menu(title = "Users", icon = "vaadin:users", order = 5)
@PageTitle("Users")
public class UsersView extends VerticalLayout {

    private final UserStore users;
    private final String appName;
    private final Grid<User> grid = new Grid<>(User.class, false);

    @Inject
    public UsersView(UserStore users, AuthConfig auth, AppInfo appInfo) {
        this.users = users;
        this.appName = appInfo.name();
        setSizeFull();

        add(new H1("Users"));
        if (!auth.authEnabled()) {
            add(new StatusBanner("Passkey sign-in is off. Turn it on with "
                    + "starter.auth.enabled=true; you can already prepare accounts here."));
        } else if (auth.bootstrapMode()) {
            add(new StatusBanner("No passkey has been registered yet. The first person to sign in "
                    + "becomes the administrator."));
        }

        configureGrid();
        add(grid, new AddUserForm(), new LoginView.SecureContextHint());
        refresh();
    }

    private void configureGrid() {
        grid.addColumn(User::username).setHeader("Username").setAutoWidth(true);
        grid.addColumn(User::displayName).setHeader("Display name").setAutoWidth(true);
        grid.addColumn(u -> String.join(", ", u.roles())).setHeader("Roles").setAutoWidth(true);
        grid.addColumn(u -> u.credentials().size()).setHeader("Passkeys").setAutoWidth(true);
        grid.addComponentColumn(this::rowActions).setHeader("").setAutoWidth(true).setFlexGrow(0);
    }

    private HorizontalLayout rowActions(User user) {
        var delete = new Button(VaadinIcon.TRASH.create(), e -> {
            users.delete(user.username());
            refresh();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        return new HorizontalLayout(new InviteButton(user, users, appName), delete);
    }

    private void refresh() {
        grid.setItems(users.all());
    }

    /** A short explanatory strip above the grid. */
    static class StatusBanner extends Paragraph {
        StatusBanner(String text) {
            super(text);
            getStyle().setPadding("var(--lumo-space-s) var(--lumo-space-m)")
                    .setBackground("var(--lumo-contrast-5pct)")
                    .setBorderRadius("var(--lumo-border-radius-m)")
                    .setMargin("0");
        }
    }

    /** Create a new person and immediately offer their registration link. */
    class AddUserForm extends Card {
        AddUserForm() {
            setWidthFull();
            var username = new TextField("Username");
            username.setPattern("[a-zA-Z0-9._-]+");
            username.setRequired(true);
            var displayName = new TextField("Display name");
            var admin = new Checkbox("Administrator");

            var create = new Button("Create user & invite link", e -> {
                String name = username.getValue() == null ? "" : username.getValue().trim();
                if (name.isBlank() || username.isInvalid()) {
                    username.setInvalid(true);
                    return;
                }
                if (users.find(name).isPresent()) {
                    Notification.show("A user named " + name + " already exists.");
                    return;
                }
                Set<String> roles = admin.getValue() ? Set.of("admin", "user") : Set.of("user");
                users.ensureUser(name, displayName.getValue(), roles);
                username.clear();
                displayName.clear();
                admin.clear();
                refresh();
                InviteButton.offer(users.find(name).orElseThrow(), users, InviteButton.DEFAULT_VALIDITY, appName);
            });
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            var row = new HorizontalLayout(username, displayName, admin, create);
            row.setAlignItems(Alignment.BASELINE);
            row.getStyle().setFlexWrap(Style.FlexWrap.WRAP);
            add(row);
        }
    }
}
