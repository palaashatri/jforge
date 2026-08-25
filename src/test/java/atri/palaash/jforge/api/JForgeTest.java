package atri.palaash.jforge.api;

import atri.palaash.jforge.engine.ModelBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(result.error().contains("definitely-not-registered"),
                    "error must name the unknown model id: " + result.error());
            assertTrue(result.error().contains("jforge model list"),
                    "error must point at 'jforge model list': " + result.error());
        }
    }

    @Test
    void bundlesDeriveFamilyFromTaskType(@TempDir Path modelRoot) throws Exception {
        try (JForge forge = JForge.create(modelRoot)) {
            var registry = forge.registry();
            var t2i = registry.byTask(atri.palaash.jforge.model.TaskType.TEXT_TO_IMAGE).get(0);
            var upscale = registry.byTask(atri.palaash.jforge.model.TaskType.IMAGE_UPSCALE).get(0);

            GenerationRequest req = GenerationRequest.builder().model(t2i.id()).build();
            ModelBundle t2iBundle = JForge.bundleFor(t2i, req);
            assertEquals("stable-diffusion", t2iBundle.family());
            assertEquals(t2i.id(), t2iBundle.id());
            assertEquals(t2i.relativePath(), t2iBundle.componentRoot());

            GenerationRequest upReq = GenerationRequest.builder().model(upscale.id()).build();
            ModelBundle upBundle = JForge.bundleFor(upscale, upReq);
            assertEquals("realesrgan", upBundle.family());
            // no invented architecture: unknown until real ingestion exists
            assertEquals("", upBundle.architecture());
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