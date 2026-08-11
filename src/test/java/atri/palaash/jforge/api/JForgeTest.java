package atri.palaash.jforge.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JForgeTest {

    @Test
    void createBuildsEngineWithoutUi(@TempDir Path modelRoot) throws Exception {
        try (JForge forge = JForge.create(modelRoot)) {
            assertNotNull(forge.pipeline());
            assertTrue(forge.registry().allModels().size() >= 3,
                    "built-in registry should contain the default models");
        }
    }

    @Test
    void unknownModelFailsActionably(@TempDir Path modelRoot) throws Exception {
        try (JForge forge = JForge.create(modelRoot)) {
            GenerationResult result = forge.generate(GenerationRequest.builder()
                    .model("definitely-not-registered")
                    .prompt("x")
                    .steps(4)
                    .width(512)
                    .height(512)
                    .build());
            assertFalse(result.success());
            assertNotNull(result.error());
        }
    }

    @Test
    void emptyPromptAndDefaultsAreValid(@TempDir Path modelRoot) throws Exception {
        try (JForge forge = JForge.create(modelRoot)) {
            // Construction + request building must never require a UI thread
            GenerationRequest req = GenerationRequest.builder()
                    .model("sd_v15_onnx")
                    .prompt("")
                    .build();
            assertTrue(req.steps() >= 1);
            assertTrue(req.batchSize() >= 1);
            assertTrue(req.width() >= 8);
            assertTrue(req.height() >= 8);
        }
    }
}