package in.virit.iot.pihelpers;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.card.Card;

import com.vaadin.flow.component.progressbar.ProgressBar;
import org.vaadin.svgvis.SvgSparkLine;

import java.io.File;
import java.util.ArrayDeque;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Dashboard showing Raspberry Pi host and JVM health, plus power actions.
 * Same spirit as the system monitor in the j-smoker project.
 */
@StyleSheet("styles/pi-helpers-system.css")
public class SystemPanel extends VerticalLayout {

    private final SystemStats stats = new SystemStats();
    private final InterfaceCard interfaces = new InterfaceCard();
    private ScheduledExecutorService executor;
    private volatile WifiInfo.Link wifiDetails = WifiInfo.Link.UNAVAILABLE;
    private volatile InterfaceStatus.Status interfaceStatus = InterfaceStatus.Status.UNAVAILABLE;
    private long lastWifiRead;

    /**
     * Only the frame is built here: the heading and the cards with their labels.
     * The readings come from a background thread once the panel is attached and
     * reach the browser over push, so opening the view never waits for the host
     * to answer; spawning iw for the WiFi details in particular takes a moment on
     * a Pi.
     */
    public SystemPanel(SystemControl systemControl) {
        addClassName("system-panel");
        add(new H1("System Monitor"));
        add(stats);
        add(interfaces);
        add(new SystemActions(systemControl));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        var ui = attachEvent.getUI();
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "system-view-refresh");
            thread.setDaemon(true);
            return thread;
        });
        lastWifiRead = 0;
        executor.scheduleAtFixedRate(() -> {
            try {
                // Everything is read here, off the UI lock; ui.access only shows it
                interfaceStatus = InterfaceStatus.read();
                show(ui, SystemStats.Reading.now(wifiDetails), interfaceStatus);
                // Link details change rarely and involve spawning iw, so refresh them
                // less often, and after the cheap readings are already on screen
                if (System.currentTimeMillis() - lastWifiRead > 15000) {
                    lastWifiRead = System.currentTimeMillis();
                    wifiDetails = WifiInfo.read();
                    show(ui, SystemStats.Reading.now(wifiDetails), interfaceStatus);
                }
            } catch (Exception ignored) {
                // UI gone or transient read failure; next tick retries
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void show(com.vaadin.flow.component.UI ui, SystemStats.Reading reading, InterfaceStatus.Status status) {
        ui.access(() -> {
            if (isAttached() && getUI().orElse(null) == ui) {
                stats.show(reading);
                interfaces.update(status);
            }
        });
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    static class SystemStats extends Card {

        private final StatBadge board = new StatBadge("Board");
        private final StatBadge os = new StatBadge("OS");
        private final StatBadge jdk = new StatBadge("JDK");
        private final StatBadge uptime = new StatBadge("Uptime");
        private final StatBadge version = new StatBadge("Version");
        private final StatBadge heapUsage = new StatBadge("Heap", "%s / %s");
        private final StatBadge processMemory = new StatBadge("Process RES");
        private final StatBadge osMemory = new StatBadge("OS mem", "%s / %s");
        private final StatBadge cpuUsage = new StatBadge("CPU", "%.0f%% proc / %.0f%% sys");
        private final StatBadge cpuTemp = new StatBadge("CPU temp");
        private final StatBadge diskUsage = new StatBadge("Disk", "%s free of %s");
        private final StatBadge network = new StatBadge("Network");
        private final StatBadge wifiLink = new StatBadge("WiFi link");
        private final StatBadge wifiSignal = new StatBadge("Signal");
        private final StatBadge wifiBitrate = new StatBadge("Bitrate");
        private final Trend cpuTrend = new Trend("%");
        private final Trend tempTrend = new Trend("°C");
        private final UsageBar memoryBar = new UsageBar();
        private final UsageBar diskBar = new UsageBar();
        private WifiInfo.Link wifi = WifiInfo.Link.UNAVAILABLE;

        SystemStats() {
            setTitle("Host & process");
            setWidthFull();
            cpuUsage.withVisual(cpuTrend);
            cpuTemp.withVisual(tempTrend);
            osMemory.withVisual(memoryBar);
            diskUsage.withVisual(diskBar);

            add(new StatGrid(board, os, jdk, uptime, version, heapUsage, processMemory,
                    osMemory, cpuUsage, cpuTemp, diskUsage,
                    network, wifiLink, wifiSignal, wifiBitrate), new GcButton());
        }

        /** Asks the JVM to collect garbage and shows the heap right after. */
        class GcButton extends Button {
            GcButton() {
                super("Run GC", e -> {
                    System.gc();
                    show(Reading.now(wifi));
                });
                addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            }
        }

        /**
         * One round of the host's readings, taken off the UI thread: files under
         * /proc and /sys, the management beans and the disk. Negative numbers
         * mean the reading is not available here.
         */
        record Reading(BoardInfo.Board host, String version, String jdk, long appUptimeSeconds,
                       long osUptimeSeconds, long heapUsed, long heapMax, long rss,
                       long osMemoryUsed, long osMemoryTotal, double processCpu, double systemCpu,
                       double cpuTemperature, String wifiSignal, long diskUsable, long diskTotal,
                       WifiInfo.Link wifi) {

            private static final long START = ManagementFactory.getRuntimeMXBean().getStartTime();
            private static String appVersion;

            static Reading now(WifiInfo.Link wifi) {
                if (appVersion == null) {
                    appVersion = readAppVersion();
                }
                Runtime rt = Runtime.getRuntime();
                long osUsed = -1, osTotal = -1;
                double proc = -1, sys = -1;
                // com.sun.management beans may be unavailable or partial in native image
                try {
                    var osMx = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                    osTotal = osMx.getTotalMemorySize();
                    osUsed = osTotal - osMx.getFreeMemorySize();
                    proc = osMx.getProcessCpuLoad();
                    sys = osMx.getCpuLoad();
                } catch (Exception ignored) {
                }
                File root = new File("/");
                return new Reading(BoardInfo.detect(), appVersion, describeJdk(),
                        (System.currentTimeMillis() - START) / 1000, readOsUptimeSeconds(),
                        rt.totalMemory() - rt.freeMemory(), rt.maxMemory(), readRssBytes(),
                        osUsed, osTotal, proc, sys, readCpuTemperature(), readWifiSignal(),
                        root.getUsableSpace(), root.getTotalSpace(), wifi);
            }
        }

        void show(Reading r) {
            this.wifi = r.wifi();
            board.setValue(r.host().describe());
            os.setValue(r.host().describeOs());
            jdk.setValue(r.jdk());
            version.setValue(r.version());

            String app = duration(r.appUptimeSeconds()) + " app";
            uptime.setValue(r.osUptimeSeconds() >= 0 ? app + " / " + duration(r.osUptimeSeconds()) + " OS" : app);
            heapUsage.setValue(mb(r.heapUsed()), mb(r.heapMax()));
            processMemory.setValue(r.rss() > 0 ? mb(r.rss()) : "N/A");

            if (r.osMemoryTotal() > 0) {
                osMemory.setValue(mb(r.osMemoryUsed()), mb(r.osMemoryTotal()));
                memoryBar.setUsage(r.osMemoryUsed(), r.osMemoryTotal());
            } else {
                osMemory.setValue("N/A", "N/A");
            }
            cpuUsage.setValue((r.processCpu() < 0 ? "N/A" : "%.0f%%".formatted(r.processCpu() * 100))
                    + " proc / " + (r.systemCpu() < 0 ? "N/A" : "%.0f%%".formatted(r.systemCpu() * 100)) + " sys");
            if (r.systemCpu() >= 0) {
                cpuTrend.add(r.systemCpu() * 100);
            }

            cpuTemp.setValue(r.cpuTemperature() >= 0 ? "%.0f°C".formatted(r.cpuTemperature()) : "N/A");
            if (r.cpuTemperature() >= 0) {
                tempTrend.add(r.cpuTemperature());
            }

            wifiSignal.setValue(r.wifiSignal());
            diskUsage.setValue(gb(r.diskUsable()), gb(r.diskTotal()));
            diskBar.setUsage(r.diskTotal() - r.diskUsable(), r.diskTotal());

            var wifi = r.wifi();
            network.setValue(wifi.ssid() != null ? wifi.ssid() : "N/A");
            wifiLink.setValue(wifi.band() != null
                    ? wifi.band() + (wifi.generation() != null ? " · " + wifi.generation() : "")
                    : "N/A");
            wifiBitrate.setValue(wifi.rxBitrate() != null || wifi.txBitrate() != null
                    ? "↓%s ↑%s Mbit/s".formatted(rateOf(wifi.rxBitrate()), rateOf(wifi.txBitrate()))
                    : "N/A");
        }

        private static String rateOf(String iwBitrate) {
            if (iwBitrate == null) {
                return "?";
            }
            int idx = iwBitrate.indexOf(" MBit/s");
            return idx > 0 ? iwBitrate.substring(0, idx) : iwBitrate;
        }

        /**
         * The last few minutes of a reading as a small line under its badge. The
         * history lives as long as the panel is open; with fewer than two readings
         * there is no line to draw, so it stays hidden.
         */
        static class Trend extends SvgSparkLine {
            private static final int SAMPLES = 150; // five minutes at the panel's two-second tick
            private final ArrayDeque<Double> values = new ArrayDeque<>();

            Trend(String unit) {
                super(48);
                setUnit(unit);
                setVisible(false);
            }

            void add(double value) {
                if (values.size() == SAMPLES) {
                    values.removeFirst();
                }
                values.addLast(value);
                setVisible(values.size() >= 2);
                if (isVisible()) {
                    setData(values.stream().mapToDouble(Double::doubleValue).toArray());
                }
            }
        }

        /** How full something is, as a slim bar under its badge; hidden while unknown. */
        static class UsageBar extends ProgressBar {
            UsageBar() {
                setVisible(false);
            }

            void setUsage(long used, long total) {
                setVisible(total > 0);
                if (total > 0) {
                    setValue((double) used / total);
                }
            }
        }

        private static String mb(long bytes) {
            return "%dM".formatted(bytes / (1024 * 1024));
        }

        private static String gb(long bytes) {
            return "%.1fG".formatted(bytes / (1024.0 * 1024 * 1024));
        }

        private static String readAppVersion() {
            try {
                var jarPath = SystemPanel.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                var modified = Files.getLastModifiedTime(Path.of(jarPath));
                return modified.toInstant().atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (Exception e) {
                return "dev";
            }
        }

        /**
         * Reads CPU temperature from the Linux thermal zone.
         * @return temperature in degrees Celsius, or -1 if not available
         */
        private static double readCpuTemperature() {
            try {
                Path zone = Path.of("/sys/class/thermal/thermal_zone0/temp");
                if (Files.exists(zone)) {
                    return Long.parseLong(Files.readString(zone).trim()) / 1000.0;
                }
            } catch (Exception ignored) {
            }
            return -1;
        }

        /**
         * Read WiFi signal level from /proc/net/wireless.
         * Format: "iface: status link level noise ..."
         * Level is typically in dBm (e.g. -45).
         */
        private static String readWifiSignal() {
            try {
                for (String line : Files.readAllLines(Path.of("/proc/net/wireless"))) {
                    line = line.trim();
                    if (line.contains(":") && !line.startsWith("Inter") && !line.startsWith("face")) {
                        // "wlan0: 0000 70. -40. -256 ..."
                        String[] parts = line.split("\\s+");
                        if (parts.length >= 4) {
                            int dbm = Integer.parseInt(parts[3].replace(".", ""));
                            String quality;
                            if (dbm >= -50) quality = "Excellent";
                            else if (dbm >= -60) quality = "Good";
                            else if (dbm >= -70) quality = "Fair";
                            else quality = "Weak";
                            return "%d dBm (%s)".formatted(dbm, quality);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return "N/A";
        }

        /**
         * The running Java: the distribution's own version string when it sets one
         * (e.g. "Temurin-25+36"), otherwise vendor and version, plus the VM, which
         * tells HotSpot builds from OpenJ9 ones such as IBM Semeru.
         */
        static String describeJdk() {
            String vendorVersion = System.getProperty("java.vendor.version");
            String release = vendorVersion != null && !vendorVersion.isBlank()
                    ? vendorVersion
                    : System.getProperty("java.vendor") + " " + System.getProperty("java.version");
            return release + " · " + System.getProperty("java.vm.name");
        }

        private static String duration(long seconds) {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            long minutes = (seconds % 3600) / 60;
            return days > 0 ? "%dd %dh %dm".formatted(days, hours, minutes)
                    : hours > 0 ? "%dh %dm".formatted(hours, minutes)
                    : "%dm".formatted(minutes);
        }

        /** Seconds since the host booted, from the first field of /proc/uptime; -1 off Linux. */
        private static long readOsUptimeSeconds() {
            try {
                String first = Files.readString(Path.of("/proc/uptime")).trim().split("\\s+")[0];
                return (long) Double.parseDouble(first);
            } catch (Exception ignored) {
                return -1;
            }
        }

        private static long readRssBytes() {
            try {
                for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                    if (line.startsWith("VmRSS:")) {
                        return Long.parseLong(line.replaceAll("[^0-9]", "")) * 1024;
                    }
                }
            } catch (Exception ignored) {
            }
            return -1;
        }
    }

    /**
     * Which buses the host exposes, as seen from /dev and /sys. Prototyping usually
     * starts with "is I2C even enabled?", so the answer sits next to the metrics.
     */
    static class InterfaceCard extends Card {

        private final StatBadge gpio = new StatBadge("GPIO");
        private final StatBadge i2c = new StatBadge("I²C");
        private final StatBadge spi = new StatBadge("SPI");
        private final StatBadge uart = new StatBadge("UART");
        private final StatBadge oneWire = new StatBadge("1-Wire");
        private final StatBadge pwm = new StatBadge("PWM");

        InterfaceCard() {
            setTitle("Interfaces");
            setWidthFull();
            add(new StatGrid(gpio, i2c, spi, uart, oneWire, pwm), PiSetup.interfaces());
        }

        void update(InterfaceStatus.Status status) {
            gpio.setValue(status.gpio());
            i2c.setValue(status.i2c());
            spi.setValue(status.spi());
            uart.setValue(status.uart());
            oneWire.setValue(status.oneWire());
            pwm.setValue(status.pwm());
        }
    }

    static class SystemActions extends Card {

        SystemActions(SystemControl systemControl) {
            setTitle("Power");
            setWidthFull();
            add(new HorizontalLayout(new RebootButton(systemControl), new ShutdownButton(systemControl)));
            add(systemControl.isEnabled() ? PiSetup.powerActions() : new Paragraph("Host power actions are disabled."));
        }

        private static void requestPowerAction(Runnable action, String successMessage) {
            try {
                action.run();
                Notification.show(successMessage);
            } catch (RuntimeException failure) {
                Notification.show("Power action failed. Check the server log and host permissions.",
                        5000, Notification.Position.MIDDLE);
            }
        }

        /** Restarts the host, once confirmed. */
        static class RebootButton extends Button {
            RebootButton(SystemControl systemControl) {
                super("Reboot", VaadinIcon.REFRESH.create(), e -> new ConfirmDialog(
                        "Reboot system?",
                        "The Raspberry Pi will restart. This takes about a minute.",
                        "Reboot", confirm -> requestPowerAction(systemControl::reboot, "Reboot requested.")) {{
                    setCancelable(true);
                    open();
                }});
                addThemeVariants(ButtonVariant.LUMO_SMALL);
                setEnabled(systemControl.isEnabled());
            }
        }

        /** Powers the host off, once confirmed; restarting it then takes physical access. */
        static class ShutdownButton extends Button {
            ShutdownButton(SystemControl systemControl) {
                super("Shutdown", VaadinIcon.POWER_OFF.create(), e -> new ConfirmDialog(
                        "Shut down system?",
                        "The Raspberry Pi will power off. You will need physical access to restart it!",
                        "Shut down", confirm -> requestPowerAction(systemControl::shutdown, "Shutdown requested.")) {{
                    setCancelable(true);
                    setConfirmButtonTheme("error primary");
                    open();
                }});
                addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);
                setEnabled(systemControl.isEnabled());
            }
        }
    }
}
