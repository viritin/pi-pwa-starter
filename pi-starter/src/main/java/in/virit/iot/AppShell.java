package in.virit.iot;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;
import com.vaadin.flow.theme.aura.Aura;

@Push
@PWA(name = "Pi Starter", shortName = "Pi Starter",
        iconPath = "icons/icon.png",
        description = "A small home for your connected devices.",
        themeColor = "#4f39f6", backgroundColor = "#d0daff", offline = true)
// App-wide styles: declared here (not on the layout) so the standalone sign-in and
// registration pages, which render without TopLayout, are themed too.
@StyleSheet(Aura.STYLESHEET)
@StyleSheet("styles/theme.css")
public class AppShell implements AppShellConfigurator {
}
