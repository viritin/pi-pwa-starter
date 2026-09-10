package in.virit.iot.pihelpers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Reads the host's WiFi link details using `iw` and NetworkManager (nmcli).
 * Fields are null when not available (wired hosts, dev machines, missing tools).
 * The metered flag doubles as a phone-hotspot hint: Android hotspots advertise
 * themselves as metered via DHCP (iPhones do not, so those go undetected).
 */
public class WifiInfo {

    public record Link(String iface, String ssid, Integer freqMhz, String band,
                       String generation, String rxBitrate, String txBitrate, String metered) {

        public static final Link UNAVAILABLE = new Link(null, null, null, null, null, null, null, null);
    }

    public static Link read() {
        String iface = findWirelessInterface();
        if (iface == null) {
            return Link.UNAVAILABLE;
        }

        String ssid = null;
        Integer freq = null;
        String rx = null;
        String tx = null;
        for (String line : run("iw", "dev", iface, "link")) {
            line = line.trim();
            if (line.startsWith("SSID:")) {
                ssid = line.substring(5).trim();
            } else if (line.startsWith("freq:")) {
                try {
                    freq = (int) Double.parseDouble(line.substring(5).trim());
                } catch (NumberFormatException ignored) {
                }
            } else if (line.startsWith("rx bitrate:")) {
                rx = line.substring(11).trim();
            } else if (line.startsWith("tx bitrate:")) {
                tx = line.substring(11).trim();
            }
        }

        return new Link(iface, ssid, freq, band(freq), generation(rx, tx), rx, tx, readMetered(iface));
    }

    private static String band(Integer freqMhz) {
        if (freqMhz == null) {
            return null;
        }
        if (freqMhz < 2500) {
            return "2.4 GHz";
        }
        if (freqMhz < 5925) {
            return "5 GHz";
        }
        return "6 GHz";
    }

    // The MCS scheme named in iw's bitrate lines reveals the 802.11 generation
    private static String generation(String rxBitrate, String txBitrate) {
        String all = (rxBitrate == null ? "" : rxBitrate) + " " + (txBitrate == null ? "" : txBitrate);
        if (all.contains("EHT-MCS")) {
            return "WiFi 7 (be)";
        }
        if (all.contains("HE-MCS")) {
            return "WiFi 6 (ax)";
        }
        if (all.contains("VHT-MCS")) {
            return "WiFi 5 (ac)";
        }
        if (all.contains("MCS")) {
            return "WiFi 4 (n)";
        }
        if (!all.isBlank()) {
            return "legacy (a/b/g)";
        }
        return null;
    }

    private static String readMetered(String iface) {
        for (String line : run("nmcli", "-t", "-f", "GENERAL.METERED", "device", "show", iface)) {
            if (line.startsWith("GENERAL.METERED:")) {
                return line.substring("GENERAL.METERED:".length()).trim();
            }
        }
        return null;
    }

    private static String findWirelessInterface() {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/net/wireless"))) {
                line = line.trim();
                int colon = line.indexOf(':');
                if (colon > 0 && !line.startsWith("Inter") && !line.startsWith("face")) {
                    return line.substring(0, colon);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static List<String> run(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return List.of();
            }
            return new String(p.getInputStream().readAllBytes()).lines().toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
