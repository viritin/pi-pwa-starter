# Pi Helpers

Reusable Vaadin components for Quarkus applications on a Raspberry Pi. The
panels are meant for the prototyping phase: check the wiring, find a sensor,
wiggle a pin or a servo before writing any application code.

Every panel is a plain component without a route, menu entry or application
layout. The example's views extend the panels and add those annotations;
another application can also embed a panel as a component. Inject the matching
service and pass it to the panel constructor. The module's `META-INF/beans.xml`
makes the CDI services discoverable from its JAR, and each panel brings its own
scoped stylesheet; the application supplies the Vaadin theme. Enable Vaadin
server push in the application's AppShell for live updates.

| Panel | Service | Needs Pi4J | What it does |
|---|---|---|---|
| `SystemPanel` | `SystemControl` | no | Host and JVM metrics, WiFi link, which interfaces (GPIO, I²C, SPI, UART, 1-Wire, PWM) the host exposes, confirmed reboot/shutdown |
| `GpioPanel` | `GpioService` | yes | Tappable 40-pin header map; configure any GPIO as input (with pull resistor) or output, drive outputs, watch inputs live |
| `I2cPanel` | `I2cService` | yes | `i2cdetect`-style bus scan, register hex dump, single register write with confirmation, hints for common addresses |
| `PwmPanel` | `PwmService` | no | Hardware PWM via sysfs: servo pulse width (µs, with angle slider and calibration) or duty cycle and frequency |
| `OneWirePanel` | `OneWireService` | no | Live readings from `/sys/bus/w1`, e.g. DS18B20 temperature probes |

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
groups on Raspberry Pi OS). Running the module without Pi4J on the classpath
while its Pi4J-backed beans are present has not been verified; if Quarkus'
build-time bean processing complains, add the two artifacts anyway.

PWM is driven through `/sys/class/pwm` directly rather than Pi4J. The released
Pi4J API (4.0.x) only accepts whole-percent duty cycles, which is far too coarse
for a servo; the fix (fractional duty cycles) is in Pi4J's main branch and will
arrive with 5.0. Switch `PwmService` over then if you prefer one API.

## Configuration

```properties
# Simulate all hardware: in-memory GPIO, a fake I²C bus with a few devices,
# a fake PWM chip and two 1-Wire probes. For development machines and tests.
starter.hardware.simulated=false

# Hide and disable the reboot/shutdown buttons.
starter.power-actions.enabled=true
```

Executing power actions requires a Linux host with passwordless sudo for
`reboot` and `shutdown`. Both buttons ask for confirmation before executing.

## Enabling interfaces on the Pi

The System panel's Interfaces card tells you what the host currently exposes.
Enable more with `sudo raspi-config` → Interface Options, or in
`/boot/firmware/config.txt`, then reboot:

```ini
dtparam=i2c_arm=on        # I²C bus 1 on GPIO2/3
dtparam=spi=on            # SPI0 on GPIO7–11
dtoverlay=pwm-2chan       # hardware PWM on GPIO18 and GPIO19
dtoverlay=w1-gpio         # 1-Wire on GPIO4
```

There is no authentication in these panels. Add access control before exposing
them beyond a trusted network: they can drive pins and write to devices.
