package atri.palaash.jforge.inference;

import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.TaskType;
import atri.palaash.jforge.storage.ModelStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link GenericOnnxService}. Verifies correct failure handling for
 * missing models and invalid ONNX files. Uses a temporary directory for model
 * storage to avoid polluting the user's real model cache.
 */
class GenericOnnxServiceTest {

    private Path tempRoot;
    private ExecutorService executor;
    private ModelStorage storage;

    /**
     * Creates a temporary directory, a virtual-thread executor, and a
     * ModelStorage pointing at the temp directory before each test.
     *
     * @throws IOException if the temp directory cannot be created
     */
    @BeforeEach
    void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("jforge-test-");
        executor = Executors.newVirtualThreadPerTaskExecutor();
        storage = new ModelStorage(tempRoot);
    }

    /**
     * Cleans up the executor and recursively deletes the temporary directory
     * after each test.
     *
     * @throws IOException if directory cleanup fails
     */
    @AfterEach
    void tearDown() throws IOException {
        if (executor != null) {
            executor.close();
        }
        if (tempRoot != null && Files.exists(tempRoot)) {
            try (var walk = Files.walk(tempRoot)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    /**
     * Verifies that running inference with dummy (non-ONNX) model bytes
     * returns a failed result with a non-blank error detail.
     *
     * @throws Exception if any unexpected error occurs
     */
    @Test
    void failsForInvalidOnnxModelBytes() throws Exception {
        ModelDescriptor descriptor = createDummyModel(TaskType.TEXT_TO_IMAGE, "sd15", "models/sd15.onnx");
        GenericOnnxService service = new GenericOnnxService(TaskType.TEXT_TO_IMAGE, storage, executor);

        InferenceResult result = service.run(new InferenceRequest(
            descriptor,
            "Robot dog playing catch in a field",
            "",
            1.0,
            42,
            1,
            512,
            512,
            "None",
            false,
            "",
            false
        )).join();

        assertFalse(result.success());
        assertFalse(result.details().isBlank());
    }

    /**
     * Verifies that running inference with a model that has no file on disk
     * returns a failed result whose detail mentions "Model not found locally".
     */
    @Test
    void failsWhenModelIsMissing() {
        ModelDescriptor descriptor = new ModelDescriptor(
                "missing",
                "Missing",
            TaskType.TEXT_TO_IMAGE,
                "models/missing.onnx",
                "https://example.com/missing.onnx",
                "test"
        );
        GenericOnnxService service = new GenericOnnxService(TaskType.TEXT_TO_IMAGE, storage, executor);

        InferenceResult result = service.run(new InferenceRequest(
            descriptor,
            "Calm narration about a sunrise",
            "",
            1.0,
            42,
            1,
            512,
            512,
            "None",
            false,
            "",
            false
        )).join();

        assertFalse(result.success());
        assertTrue(result.details().contains("Model not found locally"));
    }



    /**
     * Creates a mock model descriptor and writes a dummy file to its
     * expected storage path so that the service can find it on disk.
     *
     * @param taskType     the task type for the model
     * @param id           the model identifier
     * @param relativePath the relative storage path for the model file
     * @return a ModelDescriptor referencing the created file
     * @throws IOException if file creation fails
     */
    private ModelDescriptor createDummyModel(TaskType taskType, String id, String relativePath) throws IOException {
        ModelDescriptor descriptor = new ModelDescriptor(
                id,
                id,
                taskType,
                relativePath,
                "https://example.com/model.onnx",
                "test"
        );
        storage.ensureParentDirectory(descriptor);
        Files.writeString(storage.modelPath(descriptor), "dummy-onnx-bytes");
        return descriptor;
    }
}
