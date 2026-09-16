import io.brunoborges.jairosvg.JairoSVG;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Build-only Java source launcher; never packaged with the application. */
public class GeneratePwaIcon {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]);
        Path output = Path.of(args[1]);
        Path stamp = Path.of(args[2]);
        byte[] svg = Files.readAllBytes(source);
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(svg);
        digest.update(Files.readAllBytes(Path.of(args[3])));
        digest.update(args[4].getBytes(StandardCharsets.UTF_8));
        String fingerprint = HexFormat.of().formatHex(digest.digest());
        if (Files.isRegularFile(output) && Files.isRegularFile(stamp)
                && Files.readString(stamp).equals(fingerprint)) {
            System.out.println("PWA icon is up to date.");
            return;
        }
        byte[] png = JairoSVG.builder().fromBytes(svg)
                .outputWidth(512).outputHeight(512).toPng();
        Files.createDirectories(output.getParent());
        Files.write(output, png);
        Files.writeString(stamp, fingerprint);
        System.out.println("Generated 512 x 512 PWA icon: " + output);
    }
}
