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

import java.io.File;
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
                // Link details change rarely and involve spawning iw,
                // so refresh them (and the interface nodes) off the UI lock and less often
                if (System.currentTimeMillis() - lastWifiRead > 15000) {
                    lastWifiRead = System.currentTimeMillis();
                    wifiDetails = WifiInfo.read();
                    interfaceStatus = InterfaceStatus.read();
                }
                var wifi = wifiDetails;
                var status = interfaceStatus;
                ui.access(() -> {
                    if (isAttached() && getUI().orElse(null) == ui) {
                        stats.update(wifi);
                        interfaces.update(status);
                    }
                });
            } catch (Exception ignored) {
                // UI gone or transient read failure; next tick retries
            }
        }, 0, 2, TimeUnit.SECONDS);
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
        private final long startTimeMillis = ManagementFactory.getRuntimeMXBean().getStartTime();
        private WifiInfo.Link wifi = WifiInfo.Link.UNAVAILABLE;

        SystemStats() {
            setTitle("Host & process");
            setWidthFull();
            version.setValue(readAppVersion());
            var host = BoardInfo.detect();
            board.setValue(host.describe());
            os.setValue(host.describeOs());

            var gcButton = new Button("Run GC", e -> {
                System.gc();
                update();
            });
            gcButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            add(new StatGrid(board, os, uptime, version, heapUsage, processMemory,
                    osMemory, cpuUsage, cpuTemp, diskUsage,
                    network, wifiLink, wifiSignal, wifiBitrate), gcButton);
        }

        void update(WifiInfo.Link wifiDetails) {
            this.wifi = wifiDetails;
            update();
        }

        void update() {
            String app = duration((System.currentTimeMillis() - startTimeMillis) / 1000) + " app";
            long osSec = readOsUptimeSeconds();
            uptime.setValue(osSec >= 0 ? app + " / " + duration(osSec) + " OS" : app);

            Runtime rt = Runtime.getRuntime();
            heapUsage.setValue(mb(rt.totalMemory() - rt.freeMemory()), mb(rt.maxMemory()));

            long rss = readRssBytes();
            processMemory.setValue(rss > 0 ? mb(rss) : "N/A");

            updateOsStats();

            double temp = readCpuTemperature();
            cpuTemp.setValue(temp >= 0 ? "%.0f°C".formatted(temp) : "N/A");

            wifiSignal.setValue(readWifiSignal());

            File root = new File("/");
            diskUsage.setValue(gb(root.getUsableSpace()), gb(root.getTotalSpace()));

            network.setValue(wifi.ssid() != null ? wifi.ssid() : "N/A");
            wifiLink.setValue(wifi.band() != null
                    ? wifi.band() + (wifi.generation() != null ? " · " + wifi.generation() : "")
                    : "N/A");
            wifiBitrate.setValue(wifi.rxBitrate() != null || wifi.txBitrate() != null
                    ? "↓%s ↑%s Mbit/s".formatted(rateOf(wifi.rxBitrate()), rateOf(wifi.txBitrate()))
                    : "N/A");
        }

        private String rateOf(String iwBitrate) {
            if (iwBitrate == null) {
                return "?";
            }
            int idx = iwBitrate.indexOf(" MBit/s");
            return idx > 0 ? iwBitrate.substring(0, idx) : iwBitrate;
        }

        // com.sun.management beans may be unavailable or partial in native image
        private void updateOsStats() {
            try {
                var osMx = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                long totalOs = osMx.getTotalMemorySize();
                osMemory.setValue(mb(totalOs - osMx.getFreeMemorySize()), mb(totalOs));
                double proc = osMx.getProcessCpuLoad();
                double sys = osMx.getCpuLoad();
                cpuUsage.setValue((proc < 0 ? "N/A" : "%.0f%%".formatted(proc * 100))
                        + " proc / " + (sys < 0 ? "N/A" : "%.0f%%".formatted(sys * 100)) + " sys");
            } catch (Exception e) {
                osMemory.setValue("N/A", "N/A");
                cpuUsage.setValue("N/A");
            }
        }

        private String mb(long bytes) {
            return "%dM".formatted(bytes / (1024 * 1024));
        }

        private String gb(long bytes) {
            return "%.1fG".formatted(bytes / (1024.0 * 1024 * 1024));
        }

        private String readAppVersion() {
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
        private double readCpuTemperature() {
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
        private String readWifiSignal() {
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

        private static String duration(long seconds) {
            long days = seconds / 86400;
            long hours = (seconds % 86400) / 3600;
            long minutes = (seconds % 3600) / 60;
            return days > 0 ? "%dd %dh %dm".formatted(days, hours, minutes)
                    : hours > 0 ? "%dh %dm".formatted(hours, minutes)
                    : "%dm".formatted(minutes);
        }

        /** Seconds since the host booted, from the first field of /proc/uptime; -1 off Linux. */
        private long readOsUptimeSeconds() {
            try {
                String first = Files.readString(Path.of("/proc/uptime")).trim().split("\\s+")[0];
                return (long) Double.parseDouble(first);
            } catch (Exception ignored) {
                return -1;
            }
        }

        private long readRssBytes() {
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

        private static void requestPowerAction(Runnable action, String successMessage) {
            try {
                action.run();
                Notification.show(successMessage);
            } catch (RuntimeException failure) {
                Notification.show("Power action failed. Check the server log and host permissions.",
                        5000, Notification.Position.MIDDLE);
            }
        }


        SystemActions(SystemControl systemControl) {
            setTitle("Power");
            setWidthFull();

            var rebootButton = new Button("Reboot", VaadinIcon.REFRESH.create(), e -> {
                var dialog = new ConfirmDialog(
                        "Reboot system?",
                        "The Raspberry Pi will restart. This takes about a minute.",
                        "Reboot", confirm -> {
                    requestPowerAction(systemControl::reboot, "Reboot requested.");
                });
                dialog.setCancelable(true);
                dialog.open();
            });
            rebootButton.addThemeVariants(ButtonVariant.LUMO_SMALL);

            var shutdownButton = new Button("Shutdown", VaadinIcon.POWER_OFF.create(), e -> {
                var dialog = new ConfirmDialog(
                        "Shut down system?",
                        "The Raspberry Pi will power off. You will need physical access to restart it!",
                        "Shut down", confirm -> {
                    requestPowerAction(systemControl::shutdown, "Shutdown requested.");
                });
                dialog.setCancelable(true);
                dialog.setConfirmButtonTheme("error primary");
                dialog.open();
            });
            shutdownButton.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR);

            rebootButton.setEnabled(systemControl.isEnabled());
            shutdownButton.setEnabled(systemControl.isEnabled());
            add(new HorizontalLayout(rebootButton, shutdownButton));
            if (systemControl.isEnabled()) {
                add(PiSetup.powerActions());
            } else {
                add(new Paragraph("Host power actions are disabled."));
            }
        }
    }
}
