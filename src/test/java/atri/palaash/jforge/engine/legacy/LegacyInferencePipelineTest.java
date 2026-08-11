package atri.palaash.jforge.engine.legacy;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.engine.Capability;
import atri.palaash.jforge.engine.LoadOptions;
import atri.palaash.jforge.engine.LoadedPipeline;
import atri.palaash.jforge.engine.ModelBundle;
import atri.palaash.jforge.engine.backend.BackendDescriptor;
import atri.palaash.jforge.engine.backend.BackendSession;
import atri.palaash.jforge.engine.backend.ComputeBackend;
import atri.palaash.jforge.engine.backend.Device;
import atri.palaash.jforge.engine.backend.DeviceKind;
import atri.palaash.jforge.engine.backend.MemoryInfo;
import atri.palaash.jforge.engine.backend.PerformanceCapabilities;
import atri.palaash.jforge.inference.InferenceRequest;
import atri.palaash.jforge.inference.InferenceResult;
import atri.palaash.jforge.inference.InferenceService;
import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.TaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyInferencePipelineTest {

    private static final ModelDescriptor MODEL = new ModelDescriptor(
            "sd_v15_onnx", "SD 1.5", TaskType.TEXT_TO_IMAGE,
            "text-image/stable-diffusion-v15/unet/model.onnx", "http://x", "");

    @Test
    void capabilitiesDeclaredHonestly() {
        LegacyInferencePipeline pipeline = new LegacyInferencePipeline(req -> null, bundle -> MODEL);
        assertTrue(pipeline.capabilities().has(Capability.TEXT_TO_IMAGE));
        assertTrue(pipeline.capabilities().has(Capability.NEGATIVE_PROMPT));
        assertFalse(pipeline.capabilities().has(Capability.CONTROLNET));
        assertEquals("legacy-ort", pipeline.descriptor().id());
    }

    @Test
    void generateMapsLegacyResultAndArtifacts(@TempDir Path tempDir) throws Exception {
        Path artifact = Files.createTempFile(tempDir, "out", ".png");
        AtomicInteger calls = new AtomicInteger();
        InferenceService stub = request -> {
            calls.incrementAndGet();
            assertEquals(20L, request.seed());
            assertEquals(20, request.batch(), "legacy batch slot must carry the step count");
            return CompletableFuture.completedFuture(
                    InferenceResult.ok(artifact.toString(), "done", artifact.toString(), "image/png"));
        };

        LegacyInferencePipeline pipeline = new LegacyInferencePipeline(stub, bundle -> MODEL);
        GenerationRequest req = GenerationRequest.builder()
                .model("sd_v15_onnx").prompt("a cat").steps(20).batchSize(1).seed(20)
                .width(512).height(512).build();

        try (LoadedPipeline loaded = pipeline.load(bundle("sd_v15_onnx"), STUB_BACKEND, LoadOptions.DEFAULT)) {
            GenerationResult result = pipeline.generate(loaded, req, null, null);
            assertTrue(result.success(), result.error());
            assertEquals(1, result.images().size());
            assertEquals(artifact.toString(), result.images().get(0).path().toString());
            assertEquals(1, calls.get());
            assertFalse(result.manifest().prompt().isBlank());
            assertTrue(result.elapsedMillis() >= 0);
        }
    }

    @Test
    void batchSizeRunsSequentialDerivedSeedRuns(@TempDir Path tempDir) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        InferenceService stub = request -> {
            calls.incrementAndGet();
            Path artifact = Files.createTempFile(tempDir, "batched" + calls.get(), ".png");
            return CompletableFuture.completedFuture(
                    InferenceResult.ok("ok", "done", artifact.toString(), "image/png"));
        };

        LegacyInferencePipeline pipeline = new LegacyInferencePipeline(stub, bundle -> MODEL);
        GenerationRequest req = GenerationRequest.builder()
                .model("sd_v15_onnx").prompt("p").steps(10).batchSize(3).seed(5)
                .width(512).height(512).build();

        try (LoadedPipeline loaded = pipeline.load(bundle("sd_v15_onnx"), STUB_BACKEND, LoadOptions.DEFAULT)) {
            GenerationResult result = pipeline.generate(loaded, req, null, null);
            assertTrue(result.success(), result.error());
            assertEquals(3, result.images().size());
            assertEquals(3, calls.get());
            // derived seeds 5,6,7 in order
            assertTrue(result.images().get(0).path().toString().contains("batched1"));
            assertTrue(result.images().get(2).path().toString().contains("batched3"));
        }
    }

    @Test
    void generateReportsLegacyFailure(@TempDir Path tempDir) throws Exception {
        InferenceService stub = request -> CompletableFuture.completedFuture(
                InferenceResult.fail("Model not found locally. Download it first."));

        LegacyInferencePipeline pipeline = new LegacyInferencePipeline(stub, bundle -> MODEL);
        GenerationRequest req = GenerationRequest.builder()
                .model("sd_v15_onnx").prompt("p").steps(10).batchSize(1).seed(1)
                .width(512).height(512).build();

        try (LoadedPipeline loaded = pipeline.load(bundle("sd_v15_onnx"), STUB_BACKEND, LoadOptions.DEFAULT)) {
            GenerationResult result = pipeline.generate(loaded, req, null, null);
            assertFalse(result.success());
            assertNotNull(result.error());
            assertTrue(result.error().contains("Model not found"));
        }
    }

    @Test
    void loadRejectsUnknownBundle() {
        LegacyInferencePipeline pipeline = new LegacyInferencePipeline(req -> null, bundle -> null);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> pipeline.load(bundle("unknown"), STUB_BACKEND, LoadOptions.DEFAULT));
    }

    private static ModelBundle bundle(String id) {
        return ModelBundle.builder().id(id)
                .architecture("stable-diffusion-1.x")
                .family("stable-diffusion")
                .build();
    }

    private static final ComputeBackend STUB_BACKEND = new ComputeBackend() {
        @Override
        public BackendDescriptor descriptor() {
            return new BackendDescriptor("stub", "Stub", Set.of(DeviceKind.CPU), 1);
        }

        @Override
        public boolean supports(Device device, ModelBundle model) {
            return true;
        }

        @Override
        public BackendSession load(Device device, ModelBundle model) {
            return new BackendSession() {
                @Override
                public Device device() {
                    return device == null ? Device.UNKNOWN : device;
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public List<Device> enumerateDevices() {
            return List.of(new Device("cpu", "CPU", DeviceKind.CPU, -1, -1, ""));
        }

        @Override
        public MemoryInfo memoryInfo() {
            return MemoryInfo.UNKNOWN;
        }

        @Override
        public PerformanceCapabilities capabilities() {
            return PerformanceCapabilities.UNKNOWN;
        }
    };
}