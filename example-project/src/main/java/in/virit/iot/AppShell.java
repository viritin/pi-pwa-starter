package in.virit.iot;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.server.PWA;

@Push
@PWA(name = "Pi Starter", shortName = "Pi Starter",
        iconPath = "icons/icon.png",
        description = "A small home for your connected devices.",
        themeColor = "#4f39f6", backgroundColor = "#d0daff", offline = true)
public class AppShell implements AppShellConfigurator {
}
