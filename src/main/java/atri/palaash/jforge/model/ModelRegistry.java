package atri.palaash.jforge.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central catalogue of every AI model that jForge knows about.
 * <p>
 * Think of this as a phone book for models: it organises them by the
 * kind of task they do (text-to-image, upscaling, etc.) and keeps a
 * separate list of downloadable pipeline assets (like text encoders
 * and VAE decoders) that are needed at runtime but aren't shown in the
 * main workflow picker.  New models can be merged in at runtime from
 * remote discovery or local scanning.
 */
public class ModelRegistry {

    /** Models grouped by task type. Entries here appear in workflow combo boxes. */
    private final Map<TaskType, List<ModelDescriptor>> byTask;
    /** Pipeline-component assets (text encoders, VAEs, etc.) shown in the Model Manager UI. */
    private final List<ModelDescriptor> downloadableAssets;

    /**
     * Creates an empty registry and immediately populates it with the
     * built-in default models (SD v1.5, SDXL, Real-ESRGAN, etc.).
     */
    public ModelRegistry() {
        this.byTask = new EnumMap<>(TaskType.class);
        this.downloadableAssets = new ArrayList<>();
        registerDefaults();
    }

    /**
     * Returns a defensive copy of all models that are registered for the
     * given task type.
     *
     * @param taskType the kind of task to look up (e.g. TEXT_TO_IMAGE)
     * @return list of matching model descriptors (empty if none registered)
     */
    public List<ModelDescriptor> byTask(TaskType taskType) {
        return new ArrayList<>(byTask.getOrDefault(taskType, List.of()));
    }

    /**
     * Returns every known model — both task-grouped models and
     * downloadable pipeline assets — as a single flat list.
     *
     * @return combined list of all registered descriptors
     */
    public List<ModelDescriptor> allModels() {
        List<ModelDescriptor> output = new ArrayList<>();
        byTask.values().forEach(output::addAll);
        output.addAll(downloadableAssets);
        return output;
    }

    /**
     * Merges a list of newly discovered models into the registry.
     * <p>
     * Duplicates (matching on {@link ModelDescriptor#id()}) are silently
     * skipped.  Models whose task type is recognised as a main workflow
     * (TEXT_TO_IMAGE with a UNet or transformer path, or IMAGE_UPSCALE)
     * are also added to the task-sorted map so they appear in workflow
     * combo boxes.  This method is thread-safe.
     *
     * @param discovered list of model descriptors found by a remote or
     *                   local scan (may contain duplicates with existing entries)
     * @return the number of new models that were actually added
     */
    public synchronized int mergeDownloadableAssets(List<ModelDescriptor> discovered) {
        // Collect IDs of every model we already know about
        Set<String> knownIds = new HashSet<>();
        for (ModelDescriptor descriptor : allModels()) {
            knownIds.add(descriptor.id());
        }
        int added = 0;
        for (ModelDescriptor descriptor : discovered) {
            // knownIds.add returns false if the ID was already present
            if (knownIds.add(descriptor.id())) {
                downloadableAssets.add(descriptor);
                // Also add to the appropriate task list so it shows in workflow combos
                if (descriptor.taskType() == TaskType.TEXT_TO_IMAGE
                        && (descriptor.relativePath().contains("unet/model.onnx")
                            || descriptor.relativePath().contains("transformer/model.onnx")
                            || descriptor.sourceUrl().startsWith("hf-pytorch://"))) {
                    byTask.computeIfAbsent(TaskType.TEXT_TO_IMAGE, ignored -> new ArrayList<>())
                          .add(descriptor);
                } else if (descriptor.taskType() == TaskType.IMAGE_UPSCALE) {
                    byTask.computeIfAbsent(TaskType.IMAGE_UPSCALE, ignored -> new ArrayList<>())
                          .add(descriptor);
                }
                added++;
            }
        }
        return added;
    }

    /**
     * Populates the registry with the built-in default models that ship
     * with jForge.  Called once during construction.
     * <p>
     * Currently registers:
     * <ul>
     *   <li>Stable Diffusion v1.5 ONNX (UNet) — text-to-image</li>
     *   <li>Stable Diffusion XL Base ONNX (UNet) — text-to-image</li>
     *   <li>Real-ESRGAN ONNX — image upscaling</li>
     *   <li>SD v1.5 pipeline components (text encoder, VAE decoder,
     *       safety checker) as downloadable assets</li>
     * </ul>
     */
    private void registerDefaults() {
        // -- Text-to-image core models (appear in workflow picker) --
        List<ModelDescriptor> textToImage = new ArrayList<>();
        textToImage.add(new ModelDescriptor("sd_v15_onnx", "Stable Diffusion v1.5 ONNX", TaskType.TEXT_TO_IMAGE,
            "text-image/stable-diffusion-v15/unet/model.onnx",
            "https://huggingface.co/onnx-community/stable-diffusion-v1-5-ONNX/resolve/main/unet/model.onnx",
            "Auto-downloads UNet ONNX from onnx-community/stable-diffusion-v1-5-ONNX.",
            1_750_000_000L));
        textToImage.add(new ModelDescriptor("sdxl_base_onnx", "Stable Diffusion XL Base 1.0 ONNX", TaskType.TEXT_TO_IMAGE,
            "text-image/sdxl-base/unet/model.onnx",
            "https://huggingface.co/stabilityai/stable-diffusion-xl-base-1.0/resolve/main/unet/model.onnx",
            "Auto-downloads SDXL Base 1.0 ONNX from stabilityai. Dual text encoders, 1024×1024 native.",
            6_900_000_000L));

        // -- Image upscale model (appears in workflow picker) --
        List<ModelDescriptor> imageUpscale = new ArrayList<>();
        imageUpscale.add(new ModelDescriptor("realesrgan", "Real-ESRGAN ONNX", TaskType.IMAGE_UPSCALE,
            "text-image/realesrgan/model.onnx",
            "https://huggingface.co/imgdesignart/realesrgan-x4-onnx/resolve/main/onnx/model.onnx",
            "Auto-downloads Real-ESRGAN ONNX model.",
            62_000_000L));

        byTask.put(TaskType.TEXT_TO_IMAGE, textToImage);
        byTask.put(TaskType.IMAGE_UPSCALE, imageUpscale);

        // Pipeline component assets for SD v1.5 (shown in Model Manager, not in workflow combos)
        downloadableAssets.add(new ModelDescriptor("sd_v15_text_encoder", "SD v1.5 Text Encoder ONNX", TaskType.TEXT_TO_IMAGE,
            "text-image/stable-diffusion-v15/text_encoder/model.onnx",
            "https://huggingface.co/onnx-community/stable-diffusion-v1-5-ONNX/resolve/main/text_encoder/model.onnx",
            "Pipeline component asset for SD v1.5.",
            340_000_000L));
        downloadableAssets.add(new ModelDescriptor("sd_v15_vae_decoder", "SD v1.5 VAE Decoder ONNX", TaskType.TEXT_TO_IMAGE,
            "text-image/stable-diffusion-v15/vae_decoder/model.onnx",
            "https://huggingface.co/onnx-community/stable-diffusion-v1-5-ONNX/resolve/main/vae_decoder/model.onnx",
            "Pipeline component asset for SD v1.5.",
            335_000_000L));
        downloadableAssets.add(new ModelDescriptor("sd_v15_safety_checker", "SD v1.5 Safety Checker ONNX", TaskType.TEXT_TO_IMAGE,
            "text-image/stable-diffusion-v15/safety_checker/model.onnx",
            "https://huggingface.co/onnx-community/stable-diffusion-v1-5-ONNX/resolve/main/safety_checker/model.onnx",
            "Optional safety checker component.",
            52_000_000L));
    }
}
