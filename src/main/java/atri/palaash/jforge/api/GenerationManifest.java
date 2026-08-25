package atri.palaash.jforge.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Complete, self-describing metadata for one generated artifact, used to
 * satisfy the reproducibility contract (recreate generation, copy settings,
 * export/import manifests).
 *
 * <p>Serializable to JSON via Jackson; fields are nullable so that a
 * manifest can be reconstructed from partial metadata when importing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerationManifest(
        @JsonProperty("jforgeVersion") String jforgeVersion,
        @JsonProperty("modelId") String modelId,
        @JsonProperty("modelHash") String modelHash,
        @JsonProperty("vaeHash") String vaeHash,
        @JsonProperty("loraHashes") List<LoraHash> loraHashes,
        @JsonProperty("controlHashes") List<String> controlHashes,
        @JsonProperty("prompt") String prompt,
        @JsonProperty("negativePrompt") String negativePrompt,
        @JsonProperty("seed") Long seed,
        @JsonProperty("scheduler") String scheduler,
        @JsonProperty("steps") Integer steps,
        @JsonProperty("cfgScale") Double cfgScale,
        @JsonProperty("width") Integer width,
        @JsonProperty("height") Integer height,
        @JsonProperty("denoiseStrength") Double denoiseStrength,
        @JsonProperty("precision") String precision,
        @JsonProperty("quantization") String quantization,
        @JsonProperty("backend") String backend,
        @JsonProperty("device") String device,
        @JsonProperty("batchSize") Integer batchSize,
        @JsonProperty("generationTime") String generationTime,
        @JsonProperty("jvmVersion") String jvmVersion,
        @JsonProperty("jforgeBuild") String jforgeBuild
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoraHash(
            @JsonProperty("modelId") String modelId,
            @JsonProperty("hash") String hash,
            @JsonProperty("strength") Double strength
    ) {
        public LoraHash {
            modelId = Objects.requireNonNullElse(modelId, "");
            hash = Objects.requireNonNullElse(hash, "");
        }
    }

    public GenerationManifest {
        jforgeVersion = Objects.requireNonNullElse(jforgeVersion, "");
        modelId = Objects.requireNonNullElse(modelId, "");
        modelHash = Objects.requireNonNullElse(modelHash, "");
        vaeHash = Objects.requireNonNullElse(vaeHash, "");
        prompt = Objects.requireNonNullElse(prompt, "");
        negativePrompt = Objects.requireNonNullElse(negativePrompt, "");
        scheduler = Objects.requireNonNullElse(scheduler, "");
        precision = Objects.requireNonNullElse(precision, "");
        quantization = Objects.requireNonNullElse(quantization, "");
        backend = Objects.requireNonNullElse(backend, "");
        device = Objects.requireNonNullElse(device, "");
        generationTime = Objects.requireNonNullElse(generationTime, "");
        jvmVersion = Objects.requireNonNullElse(jvmVersion, "");
        jforgeBuild = Objects.requireNonNullElse(jforgeBuild, "");
        loraHashes = loraHashes == null ? List.of() : List.copyOf(loraHashes);
        controlHashes = controlHashes == null ? List.of() : List.copyOf(controlHashes);
    }

    public static GenerationManifest emptyManifest() {
        return new GenerationManifest("", "", "", "", List.of(), List.of(),
                "", "", null, null, null, null, null, null, null, null, null,
                "", "", null, "", "", "");
    }

    /**
     * Builds a manifest from a generation request and runtime facts.
     *
     * @param request    the request that produced the output
     * @param runtime    resolved runtime facts (backend, device, jvm version; may be null-safe strings)
     * @return a fully populated manifest
     */
    public static GenerationManifest fromRequest(GenerationRequest request, RuntimeFacts runtime) {
        return new GenerationManifest(
                runtime.jforgeVersion(),
                request.modelId(),
                runtime.modelHash(),
                runtime.vaeHash(),
                List.of(),
                List.of(),
                request.prompt(),
                request.negativePrompt(),
                request.seed(),
                request.scheduler() == null ? null : request.scheduler().name(),
                request.steps(),
                request.cfgScale(),
                request.width(),
                request.height(),
                request.denoiseStrength(),
                request.precision() == null ? null : request.precision().name(),
                request.quantization() == null ? null : request.quantization().name(),
                runtime.backend(),
                runtime.device(),
                request.batchSize(),
                runtime.generationTime(),
                runtime.jvmVersion(),
                runtime.jforgeBuild()
        );
    }

    /**
     * Amuses the "copy generation settings" workflow: derive a new
     * {@link GenerationRequest} whose fields match this manifest where present.
     */
    public GenerationRequest toRequest() {
        GenerationRequest.Builder b = GenerationRequest.builder()
                .model(modelId)
                .prompt(prompt)
                .negativePrompt(negativePrompt);
        if (seed != null) {
            b.seed(seed);
        }
        if (scheduler != null) {
            try {
                b.scheduler(SchedulerType.valueOf(scheduler));
            } catch (IllegalArgumentException ignored) {
                // keep builder default
            }
        }
        if (steps != null) {
            b.steps(steps);
        }
        if (cfgScale != null) {
            b.cfgScale(cfgScale);
        }
        if (width != null && height != null) {
            b.width(width).height(height);
        }
        if (denoiseStrength != null) {
            b.denoiseStrength(denoiseStrength);
        }
        if (precision != null) {
            Precision.fromString(precision).ifPresent(b::precision);
        }
        if (quantization != null) {
            Quantization.fromString(quantization).ifPresent(b::quantization);
        }
        if (backend != null && !backend.isBlank()) {
            b.backendId(backend);
        }
        if (device != null && !device.isBlank()) {
            b.deviceId(device);
        }
        if (batchSize != null) {
            b.batchSize(batchSize);
        }
        return b.build();
    }

    /**
     * Resolved runtime facts used when stamping a manifest.
     */
    public record RuntimeFacts(
            String jforgeVersion,
            String jforgeBuild,
            String modelHash,
            String vaeHash,
            String backend,
            String device,
            String generationTime,
            String jvmVersion
    ) {
        public RuntimeFacts {
            jforgeVersion = Objects.requireNonNullElse(jforgeVersion, "");
            jforgeBuild = Objects.requireNonNullElse(jforgeBuild, "");
            modelHash = Objects.requireNonNullElse(modelHash, "");
            vaeHash = Objects.requireNonNullElse(vaeHash, "");
            backend = Objects.requireNonNullElse(backend, "");
            device = Objects.requireNonNullElse(device, "");
            generationTime = Objects.requireNonNullElse(generationTime, "");
            jvmVersion = Objects.requireNonNullElse(jvmVersion, "");
        }
    }
}