package in.virit.iot.simulation;

import in.virit.iot.pihelpers.I2cService;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Alternative
@Priority(1)
@ApplicationScoped
public class SimulatedI2cService extends I2cService {

    private static final int MAX_BYTES = 256;
    private final Map<Integer, byte[]> devices = new HashMap<>();

    public SimulatedI2cService() {
        devices.put(0x76, bme280());
        devices.put(0x68, new byte[256]);
        devices.put(0x3C, filled((byte) 0x43));
        devices.put(0x48, ads1115());
        devices.put(0x20, filled((byte) 0xFF));
    }

    @Override
    public boolean isSimulated() {
        return true;
    }

    @Override
    public List<Integer> buses() {
        return List.of(1);
    }

    @Override
    public Scan scan(int bus) {
        var found = new ArrayList<>(devices.keySet());
        found.sort(null);
        return new Scan(found, List.of(), null);
    }

    @Override
    public byte[] readRegisters(int bus, int address, int register, int count) {
        checkArgs(address, register, count);
        var device = device(address);
        var result = new byte[count];
        for (int i = 0; i < count; i++) result[i] = readRegister(address, device, (register + i) & 0xFF);
        return result;
    }

    @Override
    public byte[] read(int bus, int address, int count) {
        return readRegisters(bus, address, 0, count);
    }

    @Override
    public void writeRegister(int bus, int address, int register, int value) {
        checkArgs(address, register, 1);
        checkByte(value);
        if (I2cService.isPortExpander(address)) device(address)[0] = (byte) value;
        else device(address)[register] = (byte) value;
    }

    @Override
    public void write(int bus, int address, int value) {
        checkArgs(address, 0, 1);
        checkByte(value);
        device(address)[0] = (byte) value;
    }

    private byte[] device(int address) {
        var device = devices.get(address);
        if (device == null) throw new IllegalStateException("No device answers at " + I2cService.hex(address) + " (simulation).");
        return device;
    }

    private static byte readRegister(int address, byte[] device, int register) {
        if (I2cService.isPortExpander(address)) return device[0];
        if (address == 0x68 && register <= 0x06) {
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
            int raw = 0x3000 + (int) (Math.sin(System.currentTimeMillis() / 3000.0) * 0x400);
            device[0] = (byte) (raw >> 8);
            device[1] = (byte) raw;
        }
        return device[register];
    }

    private static void checkArgs(int address, int register, int count) {
        if (address < 0 || address > 0x7F) throw new IllegalArgumentException("Address must be 0x00–0x7F.");
        if (register < 0 || register > 0xFF) throw new IllegalArgumentException("Register must be 0x00–0xFF.");
        if (count < 1 || count > MAX_BYTES) throw new IllegalArgumentException("Read 1–" + MAX_BYTES + " bytes at a time.");
    }

    private static void checkByte(int value) {
        if (value < 0 || value > 0xFF) throw new IllegalArgumentException("Value must be one byte (0x00–0xFF).");
    }

    private static byte[] bme280() {
        var regs = new byte[256];
        regs[0xD0] = 0x60;
        regs[0xF3] = 0x00;
        regs[0xF4] = 0x27;
        regs[0xF5] = (byte) 0xA0;
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
        Arrays.fill(regs, value);
        return regs;
    }
}
