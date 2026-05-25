package atri.palaash.jforge.inference;

import atri.palaash.jforge.model.ModelRegistry;
import atri.palaash.jforge.model.TaskType;
import atri.palaash.jforge.storage.ModelStorage;

import java.util.concurrent.Executor;

/**
 * Factory that creates appropriate {@link InferenceService} instances for each
 * {@link TaskType}. If no models are registered for a given task type, returns
 * a no-op service that immediately fails with a descriptive message.
 */
public class ServiceFactory {

    private final ModelRegistry registry;
    private final ModelStorage storage;
    private final Executor executor;

    /**
     * Constructs a ServiceFactory with the given dependencies.
     *
     * @param registry the model registry, used to check whether models exist for a task type
     * @param storage  the model storage backend, passed to created services
     * @param executor the executor for running inference tasks on background threads
     */
    public ServiceFactory(ModelRegistry registry, ModelStorage storage, Executor executor) {
        this.registry = registry;
        this.storage = storage;
        this.executor = executor;
    }

    /**
     * Creates an inference service for the given task type. Falls back to a
     * no-op failing service if no models are registered for that task.
     *
     * @param taskType the type of inference task (text-to-image, image-upscale, etc.)
     * @return an InferenceService for the specified task type
     */
    public InferenceService create(TaskType taskType) {
        if (registry.byTask(taskType).isEmpty()) {
            return request -> java.util.concurrent.CompletableFuture.completedFuture(
                    InferenceResult.fail("No models registered for this task."));
        }
        return new GenericOnnxService(taskType, storage, executor);
    }
}
