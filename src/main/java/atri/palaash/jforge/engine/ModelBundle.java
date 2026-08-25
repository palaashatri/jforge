package atri.palaash.jforge.engine;

import atri.palaash.jforge.api.Precision;
import atri.palaash.jforge.api.Quantization;
import atri.palaash.jforge.api.SchedulerType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Identity and metadata of a model as a bundle — not an arbitrary file
 * path.  A bundle describes every component a pipeline needs: the UNet /
 * transformer, text encoders, tokenizers, VAE, scheduler defaults,
 * capability metadata, license, source, hashes, and recommended settings.
 *
 * <p>This is the M0/M2 model abstraction. The current repository still
 * keys pipelines off {@code ModelDescriptor} paths; this type is the
 * target that capability-driven pipelines will consume.
 */
public record ModelBundle(
        String id,
        String displayName,
        String architecture,
        String family,
        String componentRoot,
        Map<String, String> components,
        SchedulerType defaultScheduler,
        Set<SchedulerType> supportedSchedulers,
        Precision defaultPrecision,
        Quantization defaultQuantization,
        int recommendedWidth,
        int recommendedHeight,
        int recommendedSteps,
        double recommendedCfg,
        List<String> triggerWords,
        String license,
        String source,
        Map<String, String> hashes,
        String minimumMemory,
        Map<String, String> metadata
) {
    public ModelBundle {
        Objects.requireNonNull(id, "id");
        architecture = Objects.requireNonNullElse(architecture, "");
        family = Objects.requireNonNullElse(family, "");
        componentRoot = Objects.requireNonNullElse(componentRoot, "");
        components = components == null ? Map.of() : Map.copyOf(components);
        supportedSchedulers = supportedSchedulers == null ? Set.of() : Set.copyOf(supportedSchedulers);
        defaultPrecision = Objects.requireNonNullElse(defaultPrecision, Precision.FP32);
        defaultQuantization = Objects.requireNonNullElse(defaultQuantization, Quantization.NONE);
        triggerWords = triggerWords == null ? List.of() : List.copyOf(triggerWords);
        hashes = hashes == null ? Map.of() : Map.copyOf(hashes);
        minimumMemory = Objects.requireNonNullElse(minimumMemory, "unknown");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String displayName;
        private String architecture;
        private String family;
        private String componentRoot;
        private Map<String, String> components = Map.of();
        private SchedulerType defaultScheduler = SchedulerType.DDIM;
        private Set<SchedulerType> supportedSchedulers = Set.of();
        private Precision defaultPrecision = Precision.FP32;
        private Quantization defaultQuantization = Quantization.NONE;
        private int recommendedWidth = 512;
        private int recommendedHeight = 512;
        private int recommendedSteps = 20;
        private double recommendedCfg = 7.0;
        private List<String> triggerWords = List.of();
        private String license;
        private String source;
        private Map<String, String> hashes = Map.of();
        private String minimumMemory;
        private Map<String, String> metadata = Map.of();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder architecture(String architecture) {
            this.architecture = architecture;
            return this;
        }

        public Builder family(String family) {
            this.family = family;
            return this;
        }

        public Builder componentRoot(String componentRoot) {
            this.componentRoot = componentRoot;
            return this;
        }

        public Builder components(Map<String, String> components) {
            this.components = components;
            return this;
        }

        public Builder component(String key, String path) {
            var mutable = new java.util.HashMap<>(this.components);
            mutable.put(key, path);
            this.components = mutable;
            return this;
        }

        public Builder defaultScheduler(SchedulerType defaultScheduler) {
            this.defaultScheduler = defaultScheduler;
            return this;
        }

        public Builder supportedSchedulers(Set<SchedulerType> supportedSchedulers) {
            this.supportedSchedulers = supportedSchedulers;
            return this;
        }

        public Builder defaultPrecision(Precision defaultPrecision) {
            this.defaultPrecision = defaultPrecision;
            return this;
        }

        public Builder defaultQuantization(Quantization defaultQuantization) {
            this.defaultQuantization = defaultQuantization;
            return this;
        }

        public Builder recommendedSize(int width, int height) {
            this.recommendedWidth = width;
            this.recommendedHeight = height;
            return this;
        }

        public Builder recommendedSteps(int recommendedSteps) {
            this.recommendedSteps = recommendedSteps;
            return this;
        }

        public Builder recommendedCfg(double recommendedCfg) {
            this.recommendedCfg = recommendedCfg;
            return this;
        }

        public Builder triggerWords(List<String> triggerWords) {
            this.triggerWords = triggerWords;
            return this;
        }

        public Builder license(String license) {
            this.license = license;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder hashes(Map<String, String> hashes) {
            this.hashes = hashes;
            return this;
        }

        public Builder minimumMemory(String minimumMemory) {
            this.minimumMemory = minimumMemory;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ModelBundle build() {
            return new ModelBundle(
                    id, displayName, architecture, family, componentRoot, components,
                    defaultScheduler, supportedSchedulers, defaultPrecision, defaultQuantization,
                    recommendedWidth, recommendedHeight, recommendedSteps, recommendedCfg,
                    triggerWords, license, source, hashes, minimumMemory, metadata);
        }
    }
}