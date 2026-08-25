package atri.palaash.jforge.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationRequestTest {

    @Test
    void builderProducesTypedRequest() {
        GenerationRequest req = GenerationRequest.builder()
                .model("sd_v15_onnx")
                .prompt("a cat")
                .steps(24)
                .batchSize(4)
                .seed(42)
                .width(512)
                .height(512)
                .build();

        // steps and batchSize are distinct fields, never conflated
        assertEquals(24, req.steps());
        assertEquals(4, req.batchSize());
        assertEquals("sd_v15_onnx", req.modelId());
        assertEquals(42, req.seed());
        assertEquals(512, req.width());
        assertEquals(512, req.height());
        assertEquals(7.0, req.cfgScale());
        assertEquals(SchedulerType.DDIM, req.scheduler());
    }

    @Test
    void twiceSeededBuildsProduceIdenticalRequests() {
        GenerationRequest a = GenerationRequest.builder().model("m").seed(7)
                .steps(10).build();
        GenerationRequest b = GenerationRequest.builder().model("m").seed(7)
                .steps(10).build();
        assertEquals(a, b);
    }

    @Test
    void randomSeedMarkerIsPreservedThroughBuild() {
        GenerationRequest req = GenerationRequest.builder().model("m").build();
        // The marker must survive build() so isRandomSeed() is truthful; the
        // pipeline resolves it once at the generation boundary.
        assertEquals(GenerationRequest.RANDOM_SEED, req.seed());
        assertTrue(req.isRandomSeed());

        GenerationRequest explicit = GenerationRequest.builder().model("m").seed(9).build();
        assertEquals(9, explicit.seed());
        assertTrue(!explicit.isRandomSeed());
    }

    @Test
    void rejectsInvalidSteps() {
        assertThrows(IllegalArgumentException.class,
                () -> GenerationRequest.builder().model("m").steps(0).build());
    }

    @Test
    void rejectsInvalidCfg() {
        assertThrows(IllegalArgumentException.class,
                () -> GenerationRequest.builder().model("m").cfgScale(0.8).build());
    }

    @Test
    void rejectsInvalidDenoise() {
        assertThrows(IllegalArgumentException.class,
                () -> GenerationRequest.builder().model("m").denoiseStrength(1.5).build());
    }

    @Test
    void listSettersAreCopied() {
        GenerationRequest req = GenerationRequest.builder()
                .model("m")
                .loras(List.of(new LoRAConfig("lora-1", 0.9, true)))
                .controls(List.of(new ControlInput("canny",
                        new ImageInput(Path.of("src.png")), "canny-edge", 0.8)))
                .build();
        assertEquals(1, req.loras().size());
        assertEquals(1, req.controls().size());
        // Mutation of the original list must not leak into the request
        List<LoRAConfig> original = new java.util.ArrayList<>();
        original.add(new LoRAConfig("lora-2", 0.5, false));
        GenerationRequest other = GenerationRequest.builder()
                .model("m")
                .loras(original)
                .build();
        assertEquals(1, other.loras().size());
        original.clear();
        assertEquals(1, other.loras().size(), "request must own a defensive copy");
    }

    @Test
    void toBuilderRoundTrips() {
        GenerationRequest req = GenerationRequest.builder()
                .model("m").prompt("p").steps(18).seed(5).width(768).height(768).build();
        GenerationRequest rebuilt = req.toBuilder().build();
        assertEquals(req, rebuilt);
        GenerationRequest tweaked = req.toBuilder().steps(20).build();
        assertEquals(20, tweaked.steps());
        assertEquals("m", tweaked.modelId());
    }

    @Test
    void fullRequestCarriesAllFields() {
        GenerationRequest req = GenerationRequest.builder()
                .model("sdxl")
                .prompt("p")
                .negativePrompt("n")
                .steps(30)
                .batchSize(2)
                .seed(1)
                .width(1024)
                .height(1024)
                .cfgScale(7.5)
                .denoiseStrength(0.6)
                .scheduler(SchedulerType.EULER)
                .clipSkip(1)
                .inputImage(new ImageInput(Path.of("in.png")))
                .mask(new ImageMask(Path.of("mask.png"), false))
                .vaeModelId("custom-vae")
                .precision(Precision.FP16)
                .quantization(Quantization.Q4)
                .backendId("ort-cuda")
                .deviceId("cuda:0")
                .build();

        assertEquals("n", req.negativePrompt());
        assertEquals(30, req.steps());
        assertEquals(2, req.batchSize());
        assertEquals(7.5, req.cfgScale());
        assertEquals(0.6, req.denoiseStrength());
        assertEquals(SchedulerType.EULER, req.scheduler());
        assertEquals(1, req.clipSkip());
        assertTrue(req.inputImage().isPresent());
        assertTrue(req.mask().isPresent());
        assertEquals("custom-vae", req.vaeModelId().orElse(""));
        assertEquals(Precision.FP16, req.precision());
        assertEquals(Quantization.Q4, req.quantization());
        assertEquals("ort-cuda", req.backendId().orElse(""));
        assertEquals("cuda:0", req.deviceId().orElse(""));
    }
}