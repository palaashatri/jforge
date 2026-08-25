package atri.palaash.jforge.control;

import atri.palaash.jforge.api.ControlInput;
import atri.palaash.jforge.api.ImageInput;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ControlSpecTest {
    @Test void addAndValidate() {
        ControlSpec spec = new ControlSpec();
        ControlInput c = new ControlInput("canny", new ImageInput(Path.of("a.png")), "canny", 0.8);
        spec.add(c);
        assertEquals(1, spec.size());
        assertEquals(1, spec.enabled().size());
    }

    @Test void rejectsBadStrength() {
        ControlInput bad = new ControlInput("depth", new ImageInput(Path.of("a.png")), "depth_midas", 1.5);
        assertThrows(IllegalArgumentException.class, () -> ControlSpec.validate(bad));
    }

    @Test void rejectsBadWindow() {
        ControlInput bad = new ControlInput("pose", new ImageInput(Path.of("a.png")), "openpose", 0.5, 0.8, 0.2, true);
        assertThrows(IllegalArgumentException.class, () -> ControlSpec.validate(bad));
    }

    @Test void enableDisable() {
        ControlSpec spec = new ControlSpec();
        spec.add(new ControlInput("canny", new ImageInput(Path.of("a.png")), "canny", 0.5));
        spec.setEnabled("canny", false);
        assertEquals(0, spec.enabled().size());
        spec.setEnabled("canny", true);
        assertEquals(1, spec.enabled().size());
    }

    @Test void preprocessorLookup() {
        assertEquals(Preprocessor.CANNY, Preprocessor.fromId("canny"));
        assertEquals(Preprocessor.DEPTH_MIDAS, Preprocessor.fromId("depth_midas"));
        assertEquals(Preprocessor.NONE, Preprocessor.fromId("unknown"));
        assertTrue(Preprocessor.CANNY.needsPreview());
        assertFalse(Preprocessor.TILE.needsPreview());
    }
}
