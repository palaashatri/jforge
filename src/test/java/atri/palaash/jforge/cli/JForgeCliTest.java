package atri.palaash.jforge.cli;

import atri.palaash.jforge.api.GenerationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JForgeCliTest {

    @Test
    void valueReturnsFlagArgument() {
        String[] args = {"--model", "sd_v15_onnx", "--prompt", "a cat"};
        assertEquals("sd_v15_onnx", JForgeCli.value(args, "model", null));
        assertEquals("a cat", JForgeCli.value(args, "prompt", ""));
    }

    @Test
    void valueFallsBackWhenFlagMissing() {
        String[] args = {"--model", "sd_v15_onnx"};
        assertEquals("fallback", JForgeCli.value(args, "seed", "fallback"));
        // a flag at the very end has no value argument
        assertEquals("fb", JForgeCli.value(new String[]{"--model"}, "model", "fb"));
    }

    @Test
    void intValueParsesAndFallsBack() {
        assertEquals(20, JForgeCli.intValue(new String[]{"--steps", "20"}, "steps", 5));
        assertEquals(5, JForgeCli.intValue(new String[]{}, "steps", 5));
        assertEquals(7, JForgeCli.intValue(new String[]{"--steps", "abc"}, "steps", 7));
        assertEquals(-1, JForgeCli.intValue(new String[]{"--seed", "-1"}, "seed", 0));
    }

    @Test
    void upscaleRequestCarriesInputImage(@TempDir Path tempDir) throws Exception {
        Path image = Files.createTempFile(tempDir, "in", ".png");
        GenerationRequest request = JForgeCli.upscaleRequest("realesrgan", image, 0, 0);

        assertEquals("realesrgan", request.modelId());
        assertTrue(request.inputImage().isPresent());
        assertEquals(image, request.inputImage().get().path());
        // unset dimensions fall back to pipeline defaults
        assertEquals(512, request.width());
        assertEquals(512, request.height());
    }

    @Test
    void upscaleRequestHonoursExplicitSize(@TempDir Path tempDir) throws Exception {
        Path image = Files.createTempFile(tempDir, "in", ".png");
        GenerationRequest request = JForgeCli.upscaleRequest("realesrgan", image, 1024, 768);

        assertEquals(1024, request.inputImage().get().width());
        assertEquals(768, request.inputImage().get().height());
        assertEquals(1024, request.width());
        assertEquals(768, request.height());
    }
}
