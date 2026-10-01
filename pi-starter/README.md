# Pi Starter

A small Quarkus + Vaadin Flow PWA for IoT and home automation projects.

## Run

Use JDK 25. `pi-starter` is a standalone Maven project. Install the local
helper library once from the repository root if it is not already available
from Maven Central:

```sh
cd ..
./mvnw -pl pi-helpers install
cd pi-starter
```

You can run this on the Raspberry Pi to talk to real hardware, but for most
development it is easier — and recommended — to run it **on your normal
workstation or in a container**. Off the Pi there simply is no GPIO, I²C or
sensor, so start it with the **`simulation`** profile, which swaps in in-memory
hardware and this app's own simulated services:

```sh
./mvnw -Psimulation quarkus:dev
```

On a Raspberry Pi with the hardware wired up, run it without the profile to use
the real devices:

```sh
./mvnw quarkus:dev
```

In IntelliJ, use a **Maven** run configuration for this standalone project: set
the working directory to `pi-starter` and the command line to
`-Psimulation quarkus:dev`, or activate the `simulation` profile in the Maven
tool window's Profiles panel and run `quarkus:dev` from there.

Open http://localhost:8080. About is at `/` (also `/about`), System at `/system` and Blink a LED at `/blinkled`.

Build and run the production application:

```sh
./mvnw clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Copy the **whole** `target/quarkus-app` directory when moving the application.
The Quarkus Vaadin extension builds the frontend as part of packaging.

### Deploy with boot2vm

[boot2vm](https://github.com/mstahv/boot2vm) is a convenient way to deploy this
Quarkus app to a Debian-based VM or Raspberry Pi OS. It uses SSH/rsync, systemd
and Caddy as the reverse proxy, and installs the JDK on the target.
Install the helper module first with `./mvnw -pl pi-helpers install` at the
repository root. Install the boot2vm command once per machine, then run it from
`pi-starter`:

```sh
jbang app install https://github.com/mstahv/boot2vm/blob/main/Deploy.java  # once per user
Deploy init   # prompts for host, users and settings, then provisions and deploys
Deploy        # re-run to push later updates (rsyncs only the changes, so it's fast)
```

`Deploy init` is interactive: it asks for the target host, the users and the other
settings (saved to a generated `vmhosting.*.conf`), provisions the server with
`APP_TYPE=quarkus`, and performs the first deployment. Afterwards, `Deploy` alone
packages and pushes an update to the same host — much faster than the first run,
since rsync transfers only the changed files rather than the whole application.

For PWA installation, choose an HTTPS setup from [the HTTPS guide](HTTPS.md):
Cloudflare Tunnel for remote access, DNS-01 for LAN-only public certificates, or
Caddy's internal CA on devices you administer — boot2vm's `HTTPS=internal` and
`Deploy root-cert` serve and fetch that CA certificate.

## Testing

Two layers, deliberately unequal in size.

**Browserless view tests** are the primary UI tests: `./mvnw test`. They use
Vaadin's [browserless testing](https://vaadin.com/docs/latest/testing/browserless)
(`browserless-test-quarkus`) inside the Quarkus test container, so the real
views run with development CDI alternatives, in a mocked
Vaadin environment with no browser and no frontend build. A test navigates to
a view, finds components with locators, interacts through testers and asserts
on the component tree, in milliseconds. `ViewTest` is the shared base: it scans
this package for routes, registers the custom `SwitchTester`, and offers
`awaitPush` for panels that update from their own threads through `ui.access`.
Add a test per view; `GpioViewTest` and `I2cViewTest` show the pattern.

**One Playwright smoke test**, `PwaSmokeIT`, runs with `./mvnw verify`: Failsafe
starts the packaged application under the `it` profile, headless Chromium
fetches the PWA manifest and service worker, renders the front page, navigates
once over the live connection and opens every route directly in a fresh
browser to catch a bundle that lacks a route's chunk (see
[DEV-NOTES.md](../DEV-NOTES.md)). It checks that the server delivers a working
PWA to a browser, nothing more;
what the views do is the browserless tests' job.

Playwright downloads its browser on first use. On Linux CI images missing
browser system libraries, install Chromium's prerequisites first:

```sh
./mvnw test-compile exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

Reports land in `target/surefire-reports` and `target/failsafe-reports`.

## Make it yours

- `TopLayout` uses Heisala Jetty's Viritin `MobileMainLayout`: desktop drawer
  and mobile navigation, populated from `@Menu` annotations.
- `AboutView` is a small starting page. Add another view with `@Route`,
  `layout = TopLayout.class` and `@Menu`.
- `styles/theme.css` configures Aura with a palette inspired by Screwcloud's
  Sunset Glass. It follows the device's light/dark preference and uses local
  system fonts. Metric cards wrap down to a single column on small screens.
- `AppShell` enables server push and PWA installation with the Pi Starter icon.
  Edit `src/main/resources/META-INF/resources/icons/logo.svg` to rebrand it.
- `SystemView` adds the route and navigation to `pi-helpers`’ reusable
  `SystemPanel`. That module also contains `WifiInfo` and `SystemControl`,
  adapted from Heisala Jetty’s System screen. Metrics refresh every two seconds while attached; WiFi link details
  and the Interfaces card (which of GPIO, I²C, SPI, UART, 1-Wire and PWM the
  host exposes) every 15 seconds. Leaving the view shuts down its worker.
  Missing Linux files or the optional `iw` tool produce `N/A`.
  “Uptime” shows how long the application (JVM) and the host OS have been up;
  “Version” is the application artifact timestamp.
- The **Proto Tools** screens come from `pi-helpers` and need no code here:
  `starter.proto-tools.enabled=true` in `application.properties` adds them at
  startup under a “Proto Tools” menu group, inside this application's layout
  (`TopLayout` is marked `@Layout`). They offer a tappable header map for reading
  and driving GPIOs, an I²C scanner with register dump and write, a PWM/servo
  control, live 1-Wire (DS18B20) readings and a Bluetooth LE scanner listing
  nearby devices through BlueZ, each with a folded setup hint for enabling its
  bus. Set the flag to `false` in a finished application. See
  [Pi Helpers](../pi-helpers/README.md) for details.

## Logo and PWA icon

The logo combines a pi symbol, circuit terminals and a small peach spark:
small hardware, big possibilities. Its indigo and mint palette matches the UI.
The SVG uses paths and gradients, with no fonts or external assets. The solid
square background and generous padding leave room for launcher icon masks.

During `generate-resources`, the `svg-render-maven-plugin` renders the SVG to a
512 × 512 PNG with [JairoSVG](https://github.com/brunoborges/jairosvg), in pure
Java, and packages it as `META-INF/resources/icons/icon.png`. `AppShell` selects
`icons/icon.png`;
[Vaadin generates the other PWA icon sizes](https://vaadin.com/docs/latest/flow/configuration/pwa).
No image tools beyond the project's JDK 25 are needed.

The plugin skips rendering if the PNG exists and neither the SVG nor the
settings have changed (SHA-256 fingerprint). A missing PNG or `clean` rebuild
renders it again. Generated PNGs are not committed.
To refresh the icon without packaging, run this in `pi-starter`:

```sh
./mvnw process-resources
```

After editing the SVG during a running dev session, run that command again
and reload. Installed PWAs may retain a cached icon until reinstalled.

## Blink a LED and Pi4J

The example uses Vaadin **25.3.0** and its Switch component.
`BlinkLedView` uses `led/LedService`, which drives one output through the
`Pi4JContext` shared with the pi-helpers panels; the GPIO screen shows the LED
pin as taken by the application while the LED is on. Pi4J core and the FFM
provider are dependencies of this example; `pi-helpers` declares them as
optional.

Select a **BCM GPIO number**, not a physical header pin (the default, BCM 26,
is header pin 37). Connect the GPIO through a suitable current-limiting resistor
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

To try it on a development machine, run with `-Psimulation`. The profile adds
`pi-helpers-simulation` with in-memory GPIO, I²C, PWM, 1-Wire and BLE devices,
and the CDI alternatives in `src/simulation/java` simulate this application's
own LED and BME280. They implement `Simulated` from pi-helpers, which is how the
views know to show their simulation banner; do the same for a simulation of
your own. The production jar contains none of these. Browserless tests use the
same alternatives and extend the test base from the `pi-helpers-simulation`
test jar. Normal development
and production use real device services; device/access failures display an
error and restore the last successful state.

To remove hardware support, delete `BlinkLedView`, `led/LedService` and the
GPIO/I²C/PWM/1-Wire views, remove both Pi4J dependencies and the
`pi4j.version` property, then remove the hardware browser assertions and
configuration.

References: [Vaadin Switch example](https://github.com/vaadin/docs/blob/main/src/main/java/com/vaadin/demo/component/switchcomponent/SwitchBasic.java),
[Pi4J FFM provider](https://www.pi4j.com/documentation/providers/ffm/).

## Climate sensor and pi4j-drivers

`Bme280View` is the second example view, for a sensor that produces a stream of
numbers rather than a switch. `bme280/Bme280Service` starts sampling at
application startup, keeps the last 24 hours in memory and pushes each reading
to open views; its `Bme280Sensor` CDI dependency supplies measurements, while
the service owns shared sampling, history and notifications. The view shows
the temperature on a gauge, humidity and
pressure as lines and the history as sparklines with a selectable period. The
gauge and sparkline are the `in.virit:gauge` and `in.virit:svg-visualizations`
add-ons, the same ones ScrewCloud's pi-reader draws its sensor cards with.

The driver is `Bmx280Driver` from Pi4J's [pi4j-drivers](https://github.com/Pi4J/pi4j-drivers)
library, which handles both the BME280 and the humidity-less BMP280; the view
tries addresses 0x76 and 0x77 on `starter.bme280.bus` (default 1) and
reconnects if the sensor disappears. The screen itself explains the wiring for
a breakout board and for the Waveshare Pioneer600 expansion board, whose BMP280
sits at 0x77. Nothing is sampled on a host without `/dev/i2c-1`; the status line
says so. For local UI work, run `./mvnw -Psimulation quarkus:dev` from the
`pi-starter` directory. The profile substitutes the
deterministic source in `src/simulation/java`, which supplies three hours of
initial history so curves are visible immediately. The normal production build
does not include that source; tests use it as an alternative.

## Sharing readings with Home Assistant

Kept out of `main` so the starter stays lean: the
`example/home-assistant-integration-via-mqtt` branch of this repository adds a card to the Climate view that publishes the readings over MQTT, so
Home Assistant picks them up through MQTT discovery. It builds on pi-helpers'
reusable `MqttPublisher`, `HomeAssistantDiscovery` and `HomeAssistantFinder`, and
its `HOME-ASSISTANT.md` covers configuration and a Docker setup to test against.

## Device and PWA notes

See [HTTPS for the installed PWA](HTTPS.md) for Cloudflare Tunnel remote access,
LAN-only HTTPS using your own domain and DNS-01 certificates, and a private-CA
option for `.local` hostnames.

### Server error corner

`TopLayout` adds pi-helpers' `IncidentOverlay` to the navbar: a corner warning
with a **Details** dialog whenever a server-side exception has been captured.
See [Pi Helpers](../pi-helpers/README.md#server-error-corner) for what it
catches and what it cannot.

### Plain HTTP on the LAN: what still works

Service workers need a secure context on every browser, and only `localhost`,
`*.localhost` and loopback addresses count as one without HTTPS; an mDNS name
such as `http://pwatest.local:8080` does not. What that costs depends on the
phone:

- **iPhone and iPad.** Safari's *Add to Home Screen* works for any page, HTTPS
  or not, and honours `display: standalone` from the manifest, so the app opens
  full screen without browser chrome. Only the service worker is missing: no
  offline fallback and no web push. For an app used at home with the Pi on the
  same network, that is often all you need.
- **Android.** Chrome only installs a web app over HTTPS; over HTTP the menu
  offers a home-screen shortcut that opens as an ordinary browser tab. For a
  development phone, `chrome://flags/#unsafely-treat-insecure-origin-as-secure`
  with the app's `http://` origin lifts the restriction on that device only.

Push notifications, offline caching and access from outside the LAN all need
HTTPS on both platforms; see [HTTPS.md](HTTPS.md). Flow's live views require
the server connection in any case; the offline fallback does not operate
hardware or provide live readings.

References: MDN's [Making PWAs installable](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable)
and [Secure contexts](https://developer.mozilla.org/en-US/docs/Web/Security/Defenses/Secure_Contexts),
Chrome's [installability criteria](https://web.dev/articles/install-criteria).

The GPIO, I²C and PWM screens can drive pins and write to devices; like the
power actions below, they are meant for a trusted network.

Host reboot/shutdown are enabled by default. The explicit setting
`starter.power-actions.enabled=true` is in `application.properties`; set it to
`false` to disable both the buttons and service calls. Execution requires an
appropriately configured Linux host with passwordless sudo permissions. The UI asks
for confirmation before either action. The optional passkey login (see
[AUTH.md](AUTH.md)) can gate these; otherwise add access control before exposing
diagnostics or power actions to untrusted users. “Run GC” requests JVM garbage
collection for diagnostics.

## References

- [Vaadin + Quarkus](https://vaadin.com/docs/latest/flow/integrations/quarkus)
- [Pi4J](https://www.pi4j.com/getting-started/)
