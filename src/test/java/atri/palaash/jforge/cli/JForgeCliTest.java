package atri.palaash.jforge.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JForgeCliTest {

    @Test
    void parsesStringFlags() {
        String[] args = {"generate", "--model", "sd_v15_onnx", "--prompt", "a cat"};
        assertEquals("sd_v15_onnx", JForgeCli.value(args, "model", null));
        assertEquals("a cat", JForgeCli.value(args, "prompt", null));
        assertEquals("fallback", JForgeCli.value(args, "missing", "fallback"));
    }

    @Test
    void parsesIntFlagsWithFallback() {
        String[] args = {"--steps", "24", "--width", "1024"};
        assertEquals(24, JForgeCli.intValue(args, "steps", 20));
        assertEquals(1024, JForgeCli.intValue(args, "width", 512));
        assertEquals(20, JForgeCli.intValue(args, "height", 20));
        assertEquals(20, JForgeCli.intValue(args, "bad", 20));
    }
}