package atri.palaash.jforge.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumTest {
    @Test void sha256AndVerify(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("a.bin");
        Files.writeString(f, "hello");
        String hash = Checksum.sha256(f);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(Checksum.verify(f, hash));
        assertFalse(Checksum.verify(f, "deadbeef"));
        assertTrue(Checksum.verify(f, null));
        assertTrue(Checksum.verify(f, ""));
    }
}
