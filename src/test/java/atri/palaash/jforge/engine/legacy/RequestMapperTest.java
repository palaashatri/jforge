package atri.palaash.jforge.engine.legacy;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.ImageInput;
import atri.palaash.jforge.inference.InferenceRequest;
import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.TaskType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestMapperTest {

    private static final ModelDescriptor MODEL = new ModelDescriptor(
            "sd_v15_onnx", "SD 1.5", TaskType.TEXT_TO_IMAGE,
            "text-image/stable-diffusion-v15/unet/model.onnx", "http://x", "");

    @Test
    void mapsStepsIntoLegacyBatchSlot() {
        GenerationRequest req = GenerationRequest.builder()
                .model("sd_v15_onnx").prompt("a cat").steps(24).batchSize(1)
                .seed(7).width(512).height(512).cfgScale(6.5).build();

        InferenceRequest legacy = RequestMapper.toInferenceRequest(req, MODEL, null, null);

        // The typed step count must land in the legacy "batch" slot, which
        // every legacy pipeline reads as its denoising step count.
        assertEquals(24, legacy.batch());
        assertEquals(24, req.steps());
        assertEquals(1, req.batchSize());
    }

    @Test
    void propagatesPromptFields() {
        GenerationRequest req = GenerationRequest.builder()
                .model("m").prompt("p").negativePrompt("n")
                .steps(20).batchSize(1).seed(1).width(512).height(512)
                .cfgScale(7.0).build();

        InferenceRequest legacy = RequestMapper.toInferenceRequest(req, MODEL, null, null);
        assertEquals("p", legacy.prompt());
        assertEquals("n", legacy.negativePrompt());
        assertEquals(7.0, legacy.promptWeight());
        assertEquals(1L, legacy.seed());
        assertEquals(512, legacy.width());
        assertEquals(512, legacy.height());
        assertFalse(legacy.upscale());
    }

    @Test
    void forwardsInputImagePath() {
        GenerationRequest req = GenerationRequest.builder()
                .model("m").prompt("p").steps(20).batchSize(1).seed(1)
                .width(512).height(512)
                .inputImage(new ImageInput(Path.of("C:\\in\\photo.png")))
                .build();

        InferenceRequest legacy = RequestMapper.toInferenceRequest(req, MODEL, null, null);
        assertEquals("C:\\in\\photo.png", legacy.inputImagePath());
    }

    @Test
    void preferGpuFromBackendHint() {
        GenerationRequest cpu = GenerationRequest.builder()
                .model("m").prompt("p").steps(20).batchSize(1).seed(1)
                .width(512).height(512).backendId("ort-cpu").build();
        assertFalse(RequestMapper.toInferenceRequest(cpu, MODEL, null, null).preferGpu());

        GenerationRequest gpu = GenerationRequest.builder()
                .model("m").prompt("p").steps(20).batchSize(1).seed(1)
                .width(512).height(512).backendId("ort-cuda").deviceId("cuda:0").build();
        assertTrue(RequestMapper.toInferenceRequest(gpu, MODEL, null, null).preferGpu());
    }

    @Test
    void derivedSeedsAreDeterministicAndDistinct() {
        GenerationRequest req = GenerationRequest.builder()
                .model("m").prompt("p").steps(20).batchSize(4).seed(100)
                .width(512).height(512).build();

        assertEquals(100L, RequestMapper.seedFor(req, 0));
        assertEquals(103L, RequestMapper.seedFor(req, 3));
        // same request -> same sequence
        GenerationRequest again = GenerationRequest.builder()
                .model("m").prompt("p").steps(20).batchSize(4).seed(100)
                .width(512).height(512).build();
        assertEquals(RequestMapper.seedFor(req, 2), RequestMapper.seedFor(again, 2));
    }

    @Test
    void cancellationFlagWired() {
        GenerationRequest req = GenerationRequest.builder()
                .model("m").prompt("p").steps(20).batchSize(1).seed(1)
                .width(512).height(512).build();
        AtomicBoolean cancel = new AtomicBoolean(false);
        InferenceRequest legacy = RequestMapper.toInferenceRequest(req, MODEL, null, cancel);
        assertFalse(legacy.isCancelled());
        cancel.set(true);
        assertTrue(legacy.isCancelled());
    }
}