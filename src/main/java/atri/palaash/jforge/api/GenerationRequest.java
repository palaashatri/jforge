package atri.palaash.jforge.api;

import java.util.List;
import java.util.Optional;

/**
 * A fully typed, immutable description of one generation job.
 * <p>
 * This is the public, backend-agnostic request used by the pipeline SPI,
 * the CLI, the REST server, and the embeddable Java API.  Every field has
 * explicit, separate semantics; in particular {@link #steps()} (number of
 * denoising iterations) and {@link #batchSize()} (number of independent
 * outputs) must never be conflated.
 *
 * <p>Build with {@link Builder}.  Defaults are chosen for SD-family
 * convenience but callers should set what they care about explicitly.
 */
public record GenerationRequest(
        /** Registered model identifier (e.g. "sd_v15_onnx"). */
        String modelId,
        /** Primary prompt text. */
        String prompt,
        /** Negative / undesired prompt (empty when not applicable). */
        String negativePrompt,
        /** Number of denoising steps. */
        int steps,
        /** Number of independent images to produce. */
        int batchSize,
        /** Seed for reproducible generation. */
        long seed,
        /** Output width in pixels. */
        int width,
        /** Output height in pixels. */
        int height,
        /** Classifier-free guidance scale (1.0 = no guidance). */
        double cfgScale,
        /** Denoise strength in [0,1]; 1.0 for full text-to-image. */
        double denoiseStrength,
        /** Scheduler to use. */
        SchedulerType scheduler,
        /** Number of trailing CLIP layers to skip (0 = none). */
        int clipSkip,
        /** Optional source image for img2img / inpainting / reference. */
        Optional<ImageInput> inputImage,
        /** Optional binary mask for inpainting. */
        Optional<ImageMask> mask,
        /** LoRAs to apply, in order. */
        List<LoRAConfig> loras,
        /** Conditioning controls (ControlNet-style). */
        List<ControlInput> controls,
        /** Reference images for stylization. */
        List<ReferenceImage> references,
        /** Optional custom VAE model identifier. */
        Optional<String> vaeModelId,
        /** Computation precision. */
        Precision precision,
        /** Weight quantization strategy. */
        Quantization quantization,
        /** Optional backend id (e.g. "ort-cuda"); empty = auto-select. */
        Optional<String> backendId,
        /** Optional device id (e.g. "cuda:0"); empty = auto-select. */
        Optional<String> deviceId,
        /** Additional generation options (previews, output naming). */
        GenerationOptions options
) {
    /** Sentinel seed value meaning "pick a fresh random seed". */
    public static final long RANDOM_SEED = Long.MIN_VALUE;

    public GenerationRequest {
        if (steps < 1) {
            throw new IllegalArgumentException("steps must be >= 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }
        if (width < 8 || height < 8) {
            throw new IllegalArgumentException("width and height must be >= 8");
        }
        if (cfgScale < 1.0) {
            throw new IllegalArgumentException("cfgScale must be >= 1.0");
        }
        if (denoiseStrength < 0.0 || denoiseStrength > 1.0) {
            throw new IllegalArgumentException("denoiseStrength must be in [0,1]");
        }
        if (clipSkip < 0) {
            throw new IllegalArgumentException("clipSkip must be >= 0");
        }
        if (prompt == null) {
            prompt = "";
        }
        if (negativePrompt == null) {
            negativePrompt = "";
        }
        if (scheduler == null) {
            scheduler = SchedulerType.DDIM;
        }
        if (precision == null) {
            precision = Precision.FP32;
        }
        if (quantization == null) {
            quantization = Quantization.NONE;
        }
        if (options == null) {
            options = GenerationOptions.DEFAULT;
        }
        loras = loras == null ? List.of() : List.copyOf(loras);
        controls = controls == null ? List.of() : List.copyOf(controls);
        references = references == null ? List.of() : List.copyOf(references);
        inputImage = inputImage == null ? Optional.empty() : inputImage;
        mask = mask == null ? Optional.empty() : mask;
        vaeModelId = vaeModelId == null ? Optional.empty() : vaeModelId;
        backendId = backendId == null ? Optional.empty() : backendId;
        deviceId = deviceId == null ? Optional.empty() : deviceId;
    }

    /**
     * Whether the caller requested a fresh random seed.
     *
     * @return true when {@link #seed()} is {@link #RANDOM_SEED}
     */
    public boolean isRandomSeed() {
        return seed == RANDOM_SEED;
    }

    /** Create a new builder with no values set. */
    public static Builder builder() {
        return new Builder();
    }

    /** Create a new builder pre-populated from this request (for tweaks). */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Builder for {@link GenerationRequest}.  All fields are optional and
     * default to the SD-family convenience defaults documented in the record.
     */
    public static final class Builder {
        private String modelId = "";
        private String prompt = "";
        private String negativePrompt = "";
        private int steps = 20;
        private int batchSize = 1;
        private long seed = RANDOM_SEED;
        private int width = 512;
        private int height = 512;
        private double cfgScale = 7.0;
        private double denoiseStrength = 1.0;
        private SchedulerType scheduler = SchedulerType.DDIM;
        private int clipSkip = 0;
        private Optional<ImageInput> inputImage = Optional.empty();
        private Optional<ImageMask> mask = Optional.empty();
        private List<LoRAConfig> loras = List.of();
        private List<ControlInput> controls = List.of();
        private List<ReferenceImage> references = List.of();
        private Optional<String> vaeModelId = Optional.empty();
        private Precision precision = Precision.FP32;
        private Quantization quantization = Quantization.NONE;
        private Optional<String> backendId = Optional.empty();
        private Optional<String> deviceId = Optional.empty();
        private GenerationOptions options = GenerationOptions.DEFAULT;

        public Builder() {
        }

        Builder(GenerationRequest source) {
            this.modelId = source.modelId;
            this.prompt = source.prompt;
            this.negativePrompt = source.negativePrompt;
            this.steps = source.steps;
            this.batchSize = source.batchSize;
            this.seed = source.seed;
            this.width = source.width;
            this.height = source.height;
            this.cfgScale = source.cfgScale;
            this.denoiseStrength = source.denoiseStrength;
            this.scheduler = source.scheduler;
            this.clipSkip = source.clipSkip;
            this.inputImage = source.inputImage;
            this.mask = source.mask;
            this.loras = source.loras;
            this.controls = source.controls;
            this.references = source.references;
            this.vaeModelId = source.vaeModelId;
            this.precision = source.precision;
            this.quantization = source.quantization;
            this.backendId = source.backendId;
            this.deviceId = source.deviceId;
            this.options = source.options;
        }

        public Builder model(String modelId) {
            this.modelId = modelId;
            return this;
        }

        public Builder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public Builder negativePrompt(String negativePrompt) {
            this.negativePrompt = negativePrompt;
            return this;
        }

        public Builder steps(int steps) {
            this.steps = steps;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder cfgScale(double cfgScale) {
            this.cfgScale = cfgScale;
            return this;
        }

        public Builder denoiseStrength(double denoiseStrength) {
            this.denoiseStrength = denoiseStrength;
            return this;
        }

        public Builder scheduler(SchedulerType scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public Builder clipSkip(int clipSkip) {
            this.clipSkip = clipSkip;
            return this;
        }

        public Builder inputImage(ImageInput inputImage) {
            this.inputImage = Optional.ofNullable(inputImage);
            return this;
        }

        public Builder mask(ImageMask mask) {
            this.mask = Optional.ofNullable(mask);
            return this;
        }

        public Builder loras(List<LoRAConfig> loras) {
            this.loras = loras;
            return this;
        }

        public Builder addLora(LoRAConfig lora) {
            this.loras = new java.util.ArrayList<>(this.loras);
            ((java.util.ArrayList<LoRAConfig>) this.loras).add(lora);
            return this;
        }

        public Builder controls(List<ControlInput> controls) {
            this.controls = controls;
            return this;
        }

        public Builder addControl(ControlInput control) {
            this.controls = new java.util.ArrayList<>(this.controls);
            ((java.util.ArrayList<ControlInput>) this.controls).add(control);
            return this;
        }

        public Builder references(List<ReferenceImage> references) {
            this.references = references;
            return this;
        }

        public Builder vaeModelId(String vaeModelId) {
            this.vaeModelId = Optional.ofNullable(vaeModelId);
            return this;
        }

        public Builder precision(Precision precision) {
            this.precision = precision;
            return this;
        }

        public Builder quantization(Quantization quantization) {
            this.quantization = quantization;
            return this;
        }

        public Builder backendId(String backendId) {
            this.backendId = Optional.ofNullable(backendId);
            return this;
        }

        public Builder deviceId(String deviceId) {
            this.deviceId = Optional.ofNullable(deviceId);
            return this;
        }

        public Builder options(GenerationOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Resolves a {@link #RANDOM_SEED} marker to a fresh unpredictable seed.
         *
         * @return the finished request with a concrete seed
         */
        public GenerationRequest build() {
            // The RANDOM_SEED marker is preserved here and resolved once at the
            // pipeline boundary (see LegacyInferencePipeline), so isRandomSeed()
            // is truthful and the resolved value is stamped into the manifest.
            return new GenerationRequest(
                    modelId, prompt, negativePrompt, steps, batchSize, seed,
                    width, height, cfgScale, denoiseStrength, scheduler, clipSkip,
                    inputImage, mask, loras, controls, references, vaeModelId,
                    precision, quantization, backendId, deviceId, options);
        }
    }
}