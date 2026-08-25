package atri.palaash.jforge.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAuditTest {
    @Test void safePathCheck(@TempDir Path tmp) {
        Path base = tmp.resolve("models");
        Path good = base.resolve("subdir/model.onnx");
        Path bad = tmp.resolve("other/model.onnx");
        assertTrue(SecurityAudit.isSafePath(base, good));
        assertFalse(SecurityAudit.isSafePath(base, bad));
        assertFalse(SecurityAudit.isSafePath(base, base.resolve("../escape")));
    }
    @Test void archiveEntryCheck() {
        assertTrue(SecurityAudit.isSafeArchiveEntry("model.onnx"));
        assertFalse(SecurityAudit.isSafeArchiveEntry("../escape"));
        assertFalse(SecurityAudit.isSafeArchiveEntry("/absolute"));
    }
}
