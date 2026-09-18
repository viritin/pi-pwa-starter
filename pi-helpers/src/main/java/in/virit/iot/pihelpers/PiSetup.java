package in.virit.iot.pihelpers;

/**
 * Ready-made {@link SetupHint}s for what the panels need from the host, written
 * for Raspberry Pi OS (Bookworm or later, where the boot files live in
 * /boot/firmware). Each recipe gives the commands to run, the config.txt lines
 * they correspond to, a way to check the result and a link to the documentation.
 * The commands name the account the application is running as, read from the JVM,
 * so they can be pasted as they are; a group change takes effect once the
 * application restarts (or that user logs in again).
 */
public final class PiSetup {

    static final String RASPI_CONFIG_DOCS =
            "https://www.raspberrypi.com/documentation/computers/configuration.html#raspi-config";
    static final String CONFIG_TXT_DOCS = "https://www.raspberrypi.com/documentation/computers/config_txt.html";
    static final String OVERLAYS_README = "https://github.com/raspberrypi/firmware/blob/master/boot/overlays/README";
    static final String GPIO_DOCS = "https://www.raspberrypi.com/documentation/computers/raspberry-pi.html#gpio";
    static final String PIONEER600_SCHEMATIC = "https://files.waveshare.com/upload/6/62/Pioneer600-Schematic.pdf";
    static final String BLUEZ_DOCS = "https://www.bluez.org/";
    static final String DEBIAN_BLUETOOTH_DOCS = "https://wiki.debian.org/BluetoothUser";
    static final String SUDOERS_DOCS = "https://www.sudo.ws/docs/man/sudoers.man/";

    private static final String GROUP_NOTE = "The new group applies after the application restarts "
            + "(or after that user logs in again).";

    private PiSetup() {
    }

    /** The account the application runs as, which is the one that needs the groups and sudo rights. */
    static String user() {
        return System.getProperty("user.name", "$USER");
    }

    private static String usermod(String groups) {
        return "sudo usermod -aG " + groups + " " + user();
    }

    /** I²C bus 1 on GPIO2/3 and the i2c group. */
    public static SetupHint i2c() {
        return new SetupHint("Enabling I²C on the Pi")
                .text("The I²C bus is off by default. Turn it on with raspi-config, or the equivalent line in "
                        + "/boot/firmware/config.txt, and reboot; the bus then appears as /dev/i2c-1.")
                .commands("Enable and reboot",
                        "sudo raspi-config nonint do_i2c 0",
                        "sudo reboot")
                .commands("Or add this line to /boot/firmware/config.txt",
                        "dtparam=i2c_arm=on")
                .commands("Let the application (running as " + user() + ") use the bus",
                        usermod("i2c"))
                .text(GROUP_NOTE)
                .commands("Check that the bus exists and what answers on it",
                        "ls /dev/i2c-*",
                        "sudo apt install -y i2c-tools",
                        "i2cdetect -y 1")
                .link("raspi-config", RASPI_CONFIG_DOCS)
                .link("config.txt", CONFIG_TXT_DOCS);
    }

    /** The w1-gpio overlay for DS18B20 and friends. */
    public static SetupHint oneWire() {
        return new SetupHint("Enabling 1-Wire on the Pi")
                .text("The kernel's 1-Wire driver is off by default. Turn it on and reboot; probes then show up under "
                        + "/sys/bus/w1/devices as 28-… directories that anyone may read, so no group is needed. "
                        + "Data is on GPIO4 unless config.txt says otherwise. That is also where the Pioneer600's "
                        + "1-WIRE socket is wired, pull-up included, so the plain w1-gpio overlay is all it needs "
                        + "despite the w1-gpio-pullup line in Waveshare's manual.")
                .commands("Enable and reboot",
                        "sudo raspi-config nonint do_onewire 0",
                        "sudo reboot")
                .commands("Or add this line to /boot/firmware/config.txt (the second form picks another data pin)",
                        "dtoverlay=w1-gpio",
                        "dtoverlay=w1-gpio,gpiopin=17")
                .commands("Check the probes and read one",
                        "ls /sys/bus/w1/devices/",
                        "cat /sys/bus/w1/devices/28-*/temperature")
                .link("raspi-config", RASPI_CONFIG_DOCS)
                .link("Device tree overlays", OVERLAYS_README)
                .link("Pioneer600 schematic", PIONEER600_SCHEMATIC);
    }

    /** The pwm-2chan overlay and write access to /sys/class/pwm. */
    public static SetupHint pwm() {
        return new SetupHint("Enabling hardware PWM on the Pi")
                .text("raspi-config has no switch for PWM; a device tree overlay turns it on. pwm-2chan gives two "
                        + "channels on GPIO18 (pin 12) and GPIO19 (pin 35), and after a reboot the chip appears under "
                        + "/sys/class/pwm. Raspberry Pi OS lets the gpio group write to it.")
                .commands("Add the overlay to /boot/firmware/config.txt (skipped if already there) and reboot",
                        "grep -q '^dtoverlay=pwm-2chan' /boot/firmware/config.txt "
                                + "|| echo 'dtoverlay=pwm-2chan' | sudo tee -a /boot/firmware/config.txt",
                        "sudo reboot")
                .commands("Let the application (running as " + user() + ") drive the channels",
                        usermod("gpio"))
                .text(GROUP_NOTE)
                .commands("Check that a chip is present",
                        "ls /sys/class/pwm/")
                .link("config.txt", CONFIG_TXT_DOCS)
                .link("Device tree overlays", OVERLAYS_README);
    }

    /** BlueZ, its service and the bluetooth group its D-Bus policy checks. */
    public static SetupHint bluetooth() {
        return new SetupHint("Setting up Bluetooth on the Pi")
                .text("The scanner talks to BlueZ, the Linux Bluetooth stack, over D-Bus. Three things have to be "
                        + "true: the radio is on, bluetooth.service runs, and the application user is in the "
                        + "bluetooth group, which BlueZ's D-Bus policy checks before it lets a program scan. "
                        + "The status line above names the one that is missing.")
                .commands("Radio on: a fresh Raspberry Pi OS often leaves Bluetooth soft-blocked",
                        "rfkill list bluetooth",
                        "sudo rfkill unblock bluetooth",
                        "bluetoothctl power on")
                .commands("BlueZ installed and running (Raspberry Pi OS ships it, so this only confirms)",
                        "sudo apt install -y bluez",
                        "sudo systemctl enable --now bluetooth")
                .commands("Let the application (running as " + user() + ") scan",
                        usermod("bluetooth"))
                .text(GROUP_NOTE)
                .commands("Scan from the shell as the application user to rule the application out",
                        "sudo -u " + user() + " bluetoothctl --timeout 10 scan le")
                .text("If the shell scan reports org.bluez.Error.NotReady, the radio is still off; "
                        + "AccessDenied means the group is not in effect yet.")
                .link("BlueZ", BLUEZ_DOCS)
                .link("Debian wiki: Bluetooth", DEBIAN_BLUETOOTH_DOCS);
    }

    /** Every bus the System panel's Interfaces card reports on, in one place. */
    public static SetupHint interfaces() {
        return new SetupHint("Enabling interfaces on the Pi")
                .text("Each bus is off until enabled. raspi-config flips them from the shell without the menus; "
                        + "the config.txt lines do the same by hand. Reboot afterwards. GPIO needs no enabling, "
                        + "only access to /dev/gpiochip*.")
                .commands("Enable with raspi-config, then reboot",
                        "sudo raspi-config nonint do_i2c 0        # I²C on GPIO2/3",
                        "sudo raspi-config nonint do_spi 0        # SPI0 on GPIO7–11",
                        "sudo raspi-config nonint do_serial_hw 0  # UART on GPIO14/15 (older images: do_serial 2)",
                        "sudo raspi-config nonint do_onewire 0    # 1-Wire on GPIO4",
                        "sudo reboot")
                .commands("Or the same as lines in /boot/firmware/config.txt",
                        "dtparam=i2c_arm=on",
                        "dtparam=spi=on",
                        "enable_uart=1",
                        "dtoverlay=w1-gpio",
                        "dtoverlay=pwm-2chan   # hardware PWM on GPIO18/19; raspi-config has no option for it")
                .commands("Groups that let the application (running as " + user() + ") reach the devices",
                        usermod("gpio,i2c,spi,dialout,bluetooth"))
                .text(GROUP_NOTE)
                .link("raspi-config", RASPI_CONFIG_DOCS)
                .link("config.txt", CONFIG_TXT_DOCS)
                .link("Device tree overlays", OVERLAYS_README)
                .link("GPIO header", GPIO_DOCS);
    }

    /** Passwordless sudo for exactly the two commands the Power card runs. */
    public static SetupHint powerActions() {
        return new SetupHint("Allowing reboot and shutdown without a password")
                .text("The buttons run sudo -n reboot and sudo -n shutdown as " + user() + ", the account this "
                        + "application runs as, so sudo has to accept those two commands from it without a password. "
                        + "A file in /etc/sudoers.d grants exactly that and nothing more.")
                .commands("Create the rule, lock down its permissions and check the syntax",
                        "echo \"" + user() + " ALL=(root) NOPASSWD: /usr/sbin/reboot, /usr/sbin/shutdown\" "
                                + "| sudo tee /etc/sudoers.d/010-pi-starter-power",
                        "sudo chmod 440 /etc/sudoers.d/010-pi-starter-power",
                        "sudo visudo -cf /etc/sudoers.d/010-pi-starter-power")
                .commands("Verify as " + user() + " without rebooting: prints the command when it is allowed",
                        "sudo -n -l reboot")
                .link("sudoers manual", SUDOERS_DOCS);
    }
}
