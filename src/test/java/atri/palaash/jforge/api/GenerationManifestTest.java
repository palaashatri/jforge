package atri.palaash.jforge.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationManifestTest {

    @Test
    void fromRequestCapturesGenerationSettings() {
        GenerationRequest req = GenerationRequest.builder()
                .model("sd_v15_onnx")
                .prompt("a cat")
                .negativePrompt("blur")
                .steps(25)
                .batchSize(1)
                .seed(99)
                .width(640)
                .height(384)
                .cfgScale(7.0)
                .scheduler(SchedulerType.EULER)
                .build();

        GenerationManifest.RuntimeFacts runtime = new GenerationManifest.RuntimeFacts(
                "jforge-test", "test-build", "hash-1", "vae-hash", "ort-cpu", "cpu", "42ms", "21");
        GenerationManifest manifest = GenerationManifest.fromRequest(req, runtime);

        assertEquals("sd_v15_onnx", manifest.modelId());
        assertEquals("a cat", manifest.prompt());
        assertEquals("blur", manifest.negativePrompt());
        assertEquals(25, manifest.steps());
        assertEquals(99, manifest.seed());
        assertEquals(640, manifest.width());
        assertEquals(384, manifest.height());
        assertEquals(7.0, manifest.cfgScale());
        assertEquals("EULER", manifest.scheduler());
        assertEquals("jforge-test", manifest.jforgeVersion());
        assertEquals("ort-cpu", manifest.backend());
        assertEquals("cpu", manifest.device());
    }

    @Test
    void manifestToRequestRoundTripsCoreFields() {
        GenerationManifest manifest = new GenerationManifest(
                "jforge-test", "sd_xl", "hash-1", "vae-hash",
                java.util.List.of(), java.util.List.of(),
                "a dog", "lowres", 77L, "EULER_ANCESTRAL", 22, 6.5,
                1024, 1024, 1.0, "FP16", "NONE",
                "ort-cuda", "cuda:0", 1, "123ms", "21", "test-build");

        GenerationRequest req = manifest.toRequest();
        assertEquals("sd_xl", req.modelId());
        assertEquals("a dog", req.prompt());
        assertEquals("lowres", req.negativePrompt());
        assertEquals(77, req.seed());
        assertEquals(22, req.steps());
        assertEquals(6.5, req.cfgScale());
        assertEquals(1024, req.width());
        assertEquals(1024, req.height());
        assertEquals(SchedulerType.EULER_ANCESTRAL, req.scheduler());
        assertEquals(Precision.FP16, req.precision());
        assertTrue(req.backendId().isPresent());
    }

    @Test
    void emptyManifestRoundTripsWithoutFailure() {
        GenerationManifest empty = GenerationManifest.emptyManifest();
        assertEquals("", empty.modelId());
        GenerationRequest req = empty.toRequest();
        assertEquals("", req.modelId());
        assertEquals(20, req.steps());
    }
}