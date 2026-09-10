package in.virit.iot.pihelpers;

import com.pi4j.io.i2c.I2C;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Raw I²C access for bringing up sensors and displays before any driver code
 * exists: scan a bus for devices, dump registers and poke a register. Every
 * operation opens the device, does its work and closes it again, so the
 * helpers never hold a bus open between requests.
 * <p>
 * Simulated mode answers from a few in-memory devices with plausible
 * registers (a BME280 at 0x76, a DS3231 clock at 0x68, an SSD1306 at 0x3C
 * and an ADS1115 at 0x48).
 */
@ApplicationScoped
public class I2cService {

    private static final Logger LOG = Logger.getLogger(I2cService.class);
    public static final int FIRST_ADDRESS = 0x03;
    public static final int LAST_ADDRESS = 0x77;
    private static final int MAX_BYTES = 256;

    private static final Map<Integer, String> KNOWN = Map.ofEntries(
            Map.entry(0x0D, "QMC5883L compass"), Map.entry(0x1E, "HMC5883L compass"),
            Map.entry(0x20, "MCP23017 / PCF8574 I/O expander"), Map.entry(0x23, "BH1750 light sensor"),
            Map.entry(0x27, "PCF8574 LCD backpack"), Map.entry(0x29, "VL53L0X / TSL2591"),
            Map.entry(0x38, "AHT20 humidity"), Map.entry(0x39, "TSL2561 / APDS-9960"),
            Map.entry(0x3C, "SSD1306 OLED"), Map.entry(0x3D, "SSD1306 OLED"),
            Map.entry(0x3F, "PCF8574 LCD backpack"), Map.entry(0x40, "PCA9685 / INA219 / HTU21D"),
            Map.entry(0x44, "SHT31 humidity"), Map.entry(0x48, "ADS1115 / PCF8591 ADC"),
            Map.entry(0x50, "AT24 EEPROM"), Map.entry(0x53, "ADXL345 accelerometer"),
            Map.entry(0x57, "AT24C32 EEPROM (RTC module)"), Map.entry(0x5A, "MLX90614 IR thermometer"),
            Map.entry(0x68, "MPU6050 / DS3231 / DS1307"), Map.entry(0x69, "MPU6050 (AD0 high)"),
            Map.entry(0x76, "BME280 / BMP280 / BME680"), Map.entry(0x77, "BME280 / BMP280 / BMP180"));

    @Inject
    Pi4JContext pi4j;

    private final Map<Integer, byte[]> simulated = new HashMap<>();

    public I2cService() {
        simulated.put(0x76, bme280());
        simulated.put(0x68, new byte[256]);
        simulated.put(0x3C, filled((byte) 0x43));
        simulated.put(0x48, ads1115());
    }

    public boolean isSimulated() {
        return pi4j.isSimulated();
    }

    /** Best-effort description of what usually sits at an address. */
    public static String hint(int address) {
        return KNOWN.get(address);
    }

    /** Bus numbers with a /dev/i2c-N node; the simulation offers bus 1. */
    public List<Integer> buses() {
        if (isSimulated()) {
            return List.of(1);
        }
        var buses = new ArrayList<Integer>();
        for (String name : InterfaceStatus.list(java.nio.file.Path.of("/dev"), "i2c-")) {
            try {
                buses.add(Integer.parseInt(name.substring(4)));
            } catch (NumberFormatException ignored) {
            }
        }
        return buses;
    }

    /** Addresses that acknowledge a one byte read, like {@code i2cdetect -y N}. */
    public List<Integer> scan(int bus) {
        var found = new ArrayList<Integer>();
        if (isSimulated()) {
            found.addAll(simulated.keySet());
            found.sort(null);
            return found;
        }
        for (int address = FIRST_ADDRESS; address <= LAST_ADDRESS; address++) {
            try {
                int value = withDevice(bus, address, I2C::read);
                if (value >= 0) {
                    found.add(address);
                }
            } catch (RuntimeException absent) {
                // no acknowledge from this address
            }
        }
        LOG.infof("I2C bus %d scan found %s", bus, found.stream().map(I2cService::hex).toList());
        return found;
    }

    /** Reads {@code count} bytes starting at {@code register} (the device must support auto-increment). */
    public byte[] readRegisters(int bus, int address, int register, int count) {
        checkArgs(address, register, count);
        if (isSimulated()) {
            var device = simulatedDevice(address);
            var out = new byte[count];
            for (int i = 0; i < count; i++) {
                out[i] = simulatedRead(address, device, (register + i) & 0xFF);
            }
            return out;
        }
        return withDevice(bus, address, i2c -> {
            var buffer = new byte[count];
            int n = i2c.readRegister(register, buffer, 0, count);
            return n < 0 ? new byte[0] : n == count ? buffer : java.util.Arrays.copyOf(buffer, n);
        });
    }

    /** Reads {@code count} bytes without addressing a register first. */
    public byte[] read(int bus, int address, int count) {
        checkArgs(address, 0, count);
        if (isSimulated()) {
            return readRegisters(bus, address, 0, count);
        }
        return withDevice(bus, address, i2c -> {
            var buffer = new byte[count];
            int n = i2c.read(buffer, 0, count);
            return n < 0 ? new byte[0] : n == count ? buffer : java.util.Arrays.copyOf(buffer, n);
        });
    }

    public void writeRegister(int bus, int address, int register, int value) {
        checkArgs(address, register, 1);
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value must be one byte (0x00–0xFF).");
        }
        LOG.infof("I2C bus %d device %s: write register %s = %s", bus, hex(address), hex(register), hex(value));
        if (isSimulated()) {
            simulatedDevice(address)[register] = (byte) value;
            return;
        }
        withDevice(bus, address, i2c -> i2c.writeRegister(register, (byte) value));
    }

    private <T> T withDevice(int bus, int address, Function<I2C, T> action) {
        var context = pi4j.context();
        I2C device = context.create(I2C.newConfigBuilder(context)
                .id("pi-helpers-i2c-" + bus + "-" + address).name("I2C " + hex(address))
                .bus(bus).device(address).provider("ffm-i2c").build());
        try {
            return action.apply(device);
        } finally {
            try {
                device.close();
            } catch (RuntimeException e) {
                LOG.debugf(e, "Closing I2C device %s failed", hex(address));
            }
        }
    }

    private byte[] simulatedDevice(int address) {
        var device = simulated.get(address);
        if (device == null) {
            throw new IllegalStateException("No device answers at " + hex(address) + " (simulation).");
        }
        return device;
    }

    private byte simulatedRead(int address, byte[] device, int register) {
        if (address == 0x68 && register <= 0x06) {
            // DS3231 keeps BCD time in its first registers; answer with the host clock
            var now = LocalDateTime.now();
            int value = switch (register) {
                case 0 -> now.getSecond();
                case 1 -> now.getMinute();
                case 2 -> now.getHour();
                case 3 -> now.getDayOfWeek().getValue();
                case 4 -> now.getDayOfMonth();
                case 5 -> now.getMonthValue();
                default -> now.getYear() % 100;
            };
            return (byte) (((value / 10) << 4) | (value % 10));
        }
        if (address == 0x48 && register == 0) {
            // ADS1115 conversion register wanders a little between reads
            int raw = 0x3000 + (int) (Math.sin(System.currentTimeMillis() / 3000.0) * 0x400);
            device[0] = (byte) (raw >> 8);
            device[1] = (byte) raw;
        }
        return device[register];
    }

    private static byte[] bme280() {
        var regs = new byte[256];
        regs[0xD0] = 0x60;                       // chip id
        regs[0xF3] = 0x00;                       // status: idle
        regs[0xF4] = 0x27;                       // ctrl_meas: normal mode, x1 oversampling
        regs[0xF5] = (byte) 0xA0;                // config
        byte[] raw = {0x54, (byte) 0xE6, 0x00, (byte) 0x80, 0x7E, 0x00, 0x6C, (byte) 0xB1};
        System.arraycopy(raw, 0, regs, 0xF7, raw.length);
        byte[] calib = {0x1C, 0x6E, (byte) 0x9E, 0x66, 0x32, 0x00, 0x52, (byte) 0x90, (byte) 0xC1, (byte) 0xD6,
                (byte) 0xD0, 0x0B, 0x20, 0x21, (byte) 0x45, (byte) 0xFF, (byte) 0xF9, (byte) 0xFF,
                (byte) 0xAC, 0x26, 0x0A, (byte) 0xD8, (byte) 0xBD, 0x10, 0x00, 0x4B};
        System.arraycopy(calib, 0, regs, 0x88, calib.length);
        return regs;
    }

    private static byte[] ads1115() {
        var regs = new byte[256];
        regs[1] = (byte) 0x85;
        regs[2] = (byte) 0x83;
        regs[3] = (byte) 0x80;
        regs[4] = 0x7F;
        regs[5] = (byte) 0xFF;
        return regs;
    }

    private static byte[] filled(byte value) {
        var regs = new byte[256];
        java.util.Arrays.fill(regs, value);
        return regs;
    }

    private static void checkArgs(int address, int register, int count) {
        if (address < 0 || address > 0x7F) {
            throw new IllegalArgumentException("Address must be 0x00–0x7F.");
        }
        if (register < 0 || register > 0xFF) {
            throw new IllegalArgumentException("Register must be 0x00–0xFF.");
        }
        if (count < 1 || count > MAX_BYTES) {
            throw new IllegalArgumentException("Read 1–" + MAX_BYTES + " bytes at a time.");
        }
    }

    public static String hex(int value) {
        return "0x%02X".formatted(value);
    }
}
