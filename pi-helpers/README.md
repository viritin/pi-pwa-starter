# Pi Helpers

Reusable Vaadin components for Quarkus applications. No Pi4J dependency or
hardware initialization is required.

`in.virit.iot.pihelpers.SystemPanel` contains the system diagnostics, periodic
refresh, WiFi information and confirmed reboot/shutdown actions. It has no route,
menu entry or application layout. The example's `SystemView` extends it and adds
those annotations; another application can also embed the panel as a component.

Inject `SystemControl` into your view and pass it to the panel constructor.
The module's `META-INF/beans.xml` makes the CDI service discoverable from its JAR.
Enable Vaadin server push in the application's AppShell for metric updates.
The panel includes its own scoped stylesheet; the application supplies the
Vaadin theme.

Power actions are enabled by default. Override them in the application's
`application.properties` when needed:

```properties
starter.power-actions.enabled=false
```

Executing power actions requires a Linux host with the corresponding sudo
permissions. Both buttons ask for confirmation before executing a command.
