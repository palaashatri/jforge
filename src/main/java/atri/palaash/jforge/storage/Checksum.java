package atri.palaash.jforge.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class Checksum {
    private Checksum() {}
    public static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) { throw new IOException("SHA-256 failed", e); }
    }
    public static boolean verify(Path file, String expectedHex) throws IOException {
        if (expectedHex == null || expectedHex.isBlank()) return true;
        String actual = sha256(file);
        return actual.equalsIgnoreCase(expectedHex.trim());
    }
}
