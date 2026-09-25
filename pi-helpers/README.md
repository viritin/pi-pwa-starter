# Pi Helpers

Reusable Vaadin components for Quarkus applications on a Raspberry Pi. The
panels are meant for the prototyping phase: check the wiring, find a sensor,
wiggle a pin or a servo before writing any application code.

The prototyping panels are also ready-made screens: with
`starter.proto-tools.enabled=true`, `ProtoToolsRegistrar` registers GPIO, I²C,
PWM, 1-Wire and Bluetooth LE views at startup (package `…pihelpers.tools`,
routes `gpio`, `i2c`, `pwm`, `onewire`, `ble`) under a “Proto Tools” group in
Viritin's menu. They use the application's `@Layout` class as their layout, so an
application only sets the flag. The views are `@Route(registerAtStartup = false)`
so that Vaadin's production bundle still includes the components they use;
their paths follow Vaadin's naming convention (`GpioView` → `gpio`).

Every panel itself is a plain component without a route, menu entry or
application layout, so an application can also embed one: inject the matching
service and pass it to the panel constructor (the example's `SystemView` does
this with `SystemPanel`). The module's `META-INF/beans.xml`
makes the CDI services discoverable from its JAR, and each panel brings its own
scoped stylesheet; the application supplies the Vaadin theme. Enable Vaadin
server push in the application's AppShell for live updates.

| Panel | Service | Needs Pi4J | What it does |
|---|---|---|---|
| `SystemPanel` | `SystemControl` | no | Board model and OS (`BoardInfo`, from the device tree and os-release), host and JVM metrics, WiFi link, which interfaces (GPIO, I²C, SPI, UART, 1-Wire, PWM) the host exposes, confirmed reboot/shutdown |
| `GpioPanel` | `GpioService` | yes | Tappable 40-pin header map; configure any GPIO as input (with pull resistor) or output, drive outputs, watch inputs live |
| `I2cPanel` | `I2cService` | yes | `i2cdetect`-style bus scan (addresses the application already holds are listed as such), a wiring line naming the selected bus's SDA/SCL header pins (from `pinctrl`, bus 1 by default), register hex dump, single register write with confirmation, pin toggles for PCF8574 port expanders, hints for common addresses |
| `PwmPanel` | `PwmService` | no | Hardware PWM via sysfs: servo pulse width (µs, with angle slider and calibration) or duty cycle and frequency; shows which GPIO each channel is routed to, read from `pinctrl` (or `raspi-gpio`) and the `dtoverlay=pwm…` line in config.txt |
| `OneWirePanel` | `OneWireService` | no | Live readings from `/sys/bus/w1`, e.g. DS18B20 temperature probes |
| `BlePanel` | `BleScanService` | no (BlueZ) | Live list of nearby Bluetooth LE devices: name, address, RSSI, manufacturer, advertised services and payload |

When the services simulate (`starter.hardware.simulated=true`), each Proto Tools
panel shows an orange `SimulationBanner` above its data saying what on that
screen is made up, in addition to the "Simulation" prefix in its status line.

Each panel ends with a folded *setup hint*: the commands that enable its bus on
Raspberry Pi OS, the matching `config.txt` lines, the group and sudo rules the
application needs, a check to run and links to the documentation. The commands
name the account the application is running as (read from the JVM), so they can
be pasted as they are, and every command block has a copy button. The hint opens
by itself when the hardware is missing. The recipes
are static factories on `PiSetup` (`PiSetup.i2c()`, `oneWire()`, `pwm()`,
`bluetooth()`, `interfaces()`, `powerActions()`) and the component is
`SetupHint`, so an application can add its own with
`new SetupHint("…").text(…).commands(caption, lines…).link(text, url)`.

Publishing helpers without a panel of their own (the example's Climate view
builds its Home Assistant card on them):

| Class | Needs | What it does |
|---|---|---|
| `MqttPublisher` | HiveMQ MQTT client (optional) | One MQTT connection with automatic reconnect, background connect, state and status for the UI, last will |
| `HomeAssistantDiscovery` | nothing | Pure builders for Home Assistant MQTT discovery config, state and availability topics and payloads |
| `HomeAssistantFinder` | JmDNS (optional) | Finds Home Assistant (`_home-assistant._tcp`) and MQTT brokers on the local network, checks whether port 1883 answers |
| `MqttSettings`, `SettingsStore` | nothing | Broker settings as a record, persisted as a JSON file under `starter.data-dir` |
| `MqttConfig` | nothing | The same settings from `starter.mqtt.*` as a typed SmallRye `@ConfigMapping`; when a host is set there, it wins over the UI |
| `MqttConfig` | nothing | The same settings from `application.properties` (`starter.mqtt.*`); when a host is set there, configuration overrides the UI |
| `Json` | nothing | The shared Jackson 3 mapper (Vaadin 25 brings Jackson 3); payloads and settings are annotated records |

## Pi4J and the shared context

Pi4J is an **optional** dependency of this module. `Pi4JContext` owns the one
Pi4J `Context` for the whole application and creates it on first use, never at
startup. Route all hardware access through it: Pi4J refuses to open a GPIO line
twice, and going through the shared context lets the GPIO panel show pins held
by the application's own services (the example's `LedService` does this).

Applications that use `GpioPanel`, `I2cPanel` or `Pi4JContext` add Pi4J
themselves, with the same version as pi-helpers:

```xml
<dependency>
    <groupId>com.pi4j</groupId>
    <artifactId>pi4j-core</artifactId>
    <version>${pi4j.version}</version>
</dependency>
<dependency>
    <groupId>com.pi4j</groupId>
    <artifactId>pi4j-plugin-ffm</artifactId>
    <version>${pi4j.version}</version>
</dependency>
```

The FFM provider needs `--enable-native-access=ALL-UNNAMED` on the JVM and a
user that may access `/dev/gpiochip*` and `/dev/i2c-*` (the `gpio` and `i2c`
groups on Raspberry Pi OS). The `gpio` group is not optional even for I²C-only
use: Pi4J's FFM plugin checks it while initializing and, when it is missing,
loads *no* provider at all; the symptom is `ProviderNotFoundException:
ffm-digital-output` (or `ffm-i2c`) on the first pin operation, with the real
complaint earlier in the log. `Pi4JContext` turns that into an
`IllegalStateException` that names the user and the `usermod` line, and the
GPIO, I²C and LED screens open their setup hint on it.

Release Pi4J IOs with `Pi4JContext.release(io)`, never `io.close()`: in Pi4J
4.0.2 `I2CBase.close()` does not unregister the device, so the next create at
that address fails with `IOAlreadyExistsException`. `release` goes through
`Context.shutdown(id)`, which unregisters and closes.

### Pi4J workarounds and when they can go

The helpers carry three workarounds for Pi4J 4.0.2. Each names its upstream
fix; drop it when `pi4j.version` reaches a release that contains the fix. The
`pi4j-local` profile in this module's pom builds against a locally installed Pi4J
(`./mvnw -DskipTests install` in a checkout of the fork's `my-main`, version
`5.0.0-mstahv-SNAPSHOT`) so the removal can be tried before that release:
`../mvnw -Ppi4j-local test` from this module directory (or
`./mvnw -Ppi4j-local test` from `example-project`).

| Where | Workaround | Upstream |
|---|---|---|
| `Pi4JContext.release` | `Context.shutdown(id)` instead of `io.close()` | Fixed on `main` by PRs #678 and #726, unreleased |
| `I2cService.scan` | ignores the value `read()` returns, only whether it throws | `I2CDirect.read()` sign-extends; fix on the fork's `fix/i2c-read-unsigned`, PR pending |
| `Pi4JContext.context` | throws a clear message when the FFM plugin loaded no provider | By design upstream (issue #508); report about the misleading exception pending | Running the module without Pi4J on the classpath
while its Pi4J-backed beans are present has not been verified; if Quarkus'
build-time bean processing complains, add the two artifacts anyway.

PWM is driven through `/sys/class/pwm` directly rather than Pi4J. The released
Pi4J API (4.0.x) only accepts whole-percent duty cycles, which is far too coarse
for a servo; the fix (fractional duty cycles) is in Pi4J's main branch and will
arrive with 5.0. Switch `PwmService` over then if you prefer one API.

## Bluetooth LE and BlueZ

`BlePanel` listens through BlueZ over the system D-Bus with the optional
`com.github.hypfvieh:bluez-dbus` and `dbus-java-transport-native-unixsocket`
dependencies; applications that use it add both. Scanning starts when a panel is
attached and stops when the last one leaves. The host needs `bluetooth.service`
running and the application user in the `bluetooth` group (BlueZ's D-Bus policy
checks it):

```sh
sudo apt install -y bluez
sudo systemctl enable --now bluetooth
sudo usermod -aG bluetooth $USER   # log in again or restart the service
bluetoothctl --timeout 10 scan le  # should list devices without the app
```

A nameless device is shown with its address and manufacturer id, which is often
enough to recognise it (Ruuvi is 0x0499, Apple 0x004C, Nordic 0x0059).

Rows keep their position while their signal and payload update, so the list can
be read while it is live; *Sort by signal* reorders it once, strongest first. A
fresh list starts in signal order. When a D-Bus call fails, the status line names
the step and the fix: a soft-blocked radio (`org.bluez.Error.NotReady` while
powering the adapter on) says `sudo rfkill unblock bluetooth`, `AccessDenied` says
which user to add to the `bluetooth` group, and an unreachable BlueZ points at
`bluetooth.service`. Retries back off from two seconds to thirty, and the log
carries one WARN per distinct problem rather than one per attempt.

Applications using `MqttPublisher` add `com.hivemq:hivemq-mqtt-client`, and
those using `HomeAssistantFinder` add `org.jmdns:jmdns`. mDNS only reaches the
local subnet; `starter.mdns.enabled=false` turns the lookup off (tests do this).

## Configuration

```properties
# Simulate all hardware: in-memory GPIO, a fake I²C bus with a few devices,
# a fake PWM chip, two 1-Wire probes and a handful of Bluetooth LE devices.
# For development machines and tests.
starter.hardware.simulated=false

# Hide and disable the reboot/shutdown buttons.
starter.power-actions.enabled=true
```

Executing power actions requires a Linux host with passwordless sudo for
`reboot` and `shutdown`, and nothing else. As the application user:

```sh
echo "$USER ALL=(root) NOPASSWD: /usr/sbin/reboot, /usr/sbin/shutdown" | sudo tee /etc/sudoers.d/010-pi-starter-power
sudo chmod 440 /etc/sudoers.d/010-pi-starter-power
sudo visudo -cf /etc/sudoers.d/010-pi-starter-power
sudo -n -l reboot   # prints the command when the rule works
```

Both buttons ask for confirmation before executing.

## Enabling interfaces on the Pi

The System panel's Interfaces card tells you what the host currently exposes,
and its setup hint has the same steps as below with copy buttons. From the shell:

```sh
sudo raspi-config nonint do_i2c 0        # I²C bus 1 on GPIO2/3
sudo raspi-config nonint do_spi 0        # SPI0 on GPIO7–11
sudo raspi-config nonint do_serial_hw 0  # UART on GPIO14/15 (older images: do_serial 2)
sudo raspi-config nonint do_onewire 0    # 1-Wire on GPIO4
sudo reboot
```

Or the equivalent lines in `/boot/firmware/config.txt` (the only way for PWM):

```ini
dtparam=i2c_arm=on        # I²C bus 1 on GPIO2/3
dtparam=spi=on            # SPI0 on GPIO7–11
enable_uart=1             # UART on GPIO14/15
dtoverlay=pwm-2chan       # hardware PWM on GPIO18 and GPIO19
dtoverlay=w1-gpio         # 1-Wire on GPIO4
```

The application user needs the matching groups; they apply after logging in
again or restarting the service:

```sh
sudo usermod -aG gpio,i2c,spi,dialout,bluetooth $USER
```

There is no authentication in these panels. Add access control before exposing
them beyond a trusted network: they can drive pins and write to devices.
