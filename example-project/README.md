# Pi Starter

A small Quarkus + Vaadin Flow PWA for IoT and home automation projects.
This is the runnable example that a future Maven archetype will generate.

## Run

Use JDK 25. From the repository root, start both modules in the reactor:

```sh
./mvnw -pl example-project -am quarkus:dev
```

Alternatively, first run `./mvnw install` at the repository root to install
the parent and `pi-helpers`, then use the wrapper in this directory:

```sh
./mvnw quarkus:dev
```

Open http://localhost:8080. About is at `/` (also `/about`), System at `/system` and Blink a LED at `/blink-led`.

Build and run the production application:

```sh
./mvnw clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Copy the **whole** `target/quarkus-app` directory when moving the application.
The Quarkus Vaadin extension builds the frontend as part of packaging.

## Browser integration test

From the repository root, build both modules and run the smoke test with:

```sh
./mvnw verify
```

Failsafe runs `ApplicationIT`: Quarkus starts the packaged server on its test
port (8081 by default) and stops it after the test. Headless Chromium opens the
application, checks all three navigation links and opens
every view URL directly. It also selects a GPIO and toggles the LED Switch in
the explicitly configured `it` simulation profile. No manually started server is needed. A missing view
or failed Vaadin initialization fails the test.

Playwright downloads its browsers on first use. On Linux CI images missing
browser system libraries, install Chromium's prerequisites first:

```sh
./mvnw test-compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

Reports are written to `target/failsafe-reports`. This tests the packaged
application; `quarkus:dev` remains the development entry point.

## Make it yours

- `TopLayout` uses Heisala Jetty's Viritin `MobileMainLayout`: desktop drawer
  and mobile navigation, populated from `@Menu` annotations.
- `AboutView` is a small starting page. Add another view with `@Route`,
  `layout = TopLayout.class` and `@Menu`.
- `styles/starter.css` configures Aura with a palette inspired by Screwcloud's
  Sunset Glass. It follows the device's light/dark preference and uses local
  system fonts. Metric cards wrap down to a single column on small screens.
- `AppShell` enables server push and PWA installation. Replace the default
  Vaadin icon with your own PNG using `@PWA(iconPath = "icons/icon.png")`.
- `SystemView` adds the route and navigation to `pi-helpers`’ reusable
  `SystemPanel`. That module also contains `WifiInfo` and `SystemControl`,
  adapted from Heisala Jetty’s System screen. Metrics refresh every two seconds while attached; WiFi link details
  every 15 seconds. Leaving the view shuts down its worker.
  Missing Linux files or optional `iw`/`nmcli` tools produce `N/A`.
  “Uptime” is JVM uptime; “Version” is the application artifact timestamp.
  The hotspot field is only a metered-connection heuristic.

## Blink a LED and Pi4J

The example uses Vaadin **25.3.0-beta2** and its Switch component.
`BlinkLedView` uses `led/LedService`, the only hardware-specific service.
Pi4J core and the FFM provider are dependencies of this example, not of
`pi-helpers`.

Select a **BCM GPIO number**, not a physical header pin (for example BCM 17
is header pin 11). Connect the GPIO through a suitable current-limiting resistor
to the LED anode and connect its cathode to GND. The Switch selects HIGH
(3.3 V) or LOW (0 V); it does not provide PWM or timed blinking.

The service initializes Pi4J only on the first switch operation. On a Raspberry
Pi, the application user needs access to the GPIO device. Run the packaged app
with Java native access enabled for the FFM provider:

```sh
java --enable-native-access=ALL-UNNAMED -jar target/quarkus-app/quarkus-run.jar
```

The selected output is shared across browsers. Turn it off before changing its
GPIO number. Leaving the view keeps the output as selected; orderly application
shutdown requests LOW and releases Pi4J. The display reports the last successful
output command, not an independent measurement of the LED.

To try it on a development machine, set `starter.gpio.simulated=true`.
The view explicitly displays “Simulation” and no hardware is accessed.
The browser IT selects this mode through the `it` profile. Normal development
and production default to real GPIO; device/access failures display an error
and restore the last successful state.

To remove hardware support, delete `BlinkLedView` and `led/LedService`,
remove both Pi4J dependencies and the parent's `pi4j.version` property,
then remove the LED-specific browser assertions and GPIO configuration.

References: [Vaadin Switch example](https://github.com/vaadin/docs/blob/main/src/main/java/com/vaadin/demo/component/switchcomponent/SwitchBasic.java),
[Pi4J FFM provider](https://www.pi4j.com/documentation/providers/ffm/).

## Device and PWA notes

PWA installation/service workers require HTTPS, except on localhost.
Accessing a Pi through plain HTTP on the LAN is sufficient for the web UI,
but not PWA installation. Flow's live views require the server connection;
the offline fallback does not operate hardware or provide live readings.

Host reboot/shutdown are enabled by default. The explicit setting
`starter.power-actions.enabled=true` is in `application.properties`; set it to
`false` to disable both the buttons and service calls. Execution requires an
appropriately configured Linux host with passwordless sudo permissions. The UI asks
for confirmation before either action. There is no authentication in this
starter; add access control before exposing diagnostics or power actions to
untrusted users. “Run GC” requests JVM garbage collection for diagnostics.

Raspberry Pi deployment, native-image builds and memory tuning are future work.
The application has not yet been validated on a Pi. In particular, do not assume
the original ARMv6 Pi Zero can run this Java 25 stack; validate the target JVM
and architecture first. Pi Zero 2 W is a different hardware target.

## References

- [Vaadin + Quarkus](https://vaadin.com/docs/latest/flow/integrations/quarkus)
- [Pi4J](https://www.pi4j.com/getting-started/)
- Local references: `../related-projects-and-examples/heisala-jetty/heisala-jetty-server`
  and `../related-projects-and-examples/screwcloud/server`.
