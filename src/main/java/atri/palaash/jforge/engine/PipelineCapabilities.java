package atri.palaash.jforge.engine;

import atri.palaash.jforge.api.SchedulerType;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Declared capabilities of a pipeline/model. The UI renders controls
 * based on this metadata so unrelated controls are never shown.
 */
public final class PipelineCapabilities {

    private final Set<Capability> capabilities;
    private final Set<SchedulerType> supportedSchedulers;
    private final SchedulerType recommendedScheduler;
    private final int maxResolution;
    private final int minSteps;
    private final int maxSteps;

    private PipelineCapabilities(Builder b) {
        this.capabilities = b.capabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(b.capabilities);
        this.supportedSchedulers = b.supportedSchedulers.isEmpty()
                ? EnumSet.noneOf(SchedulerType.class)
                : EnumSet.copyOf(b.supportedSchedulers);
        this.recommendedScheduler = b.recommendedScheduler;
        this.maxResolution = b.maxResolution;
        this.minSteps = b.minSteps;
        this.maxSteps = b.maxSteps;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Set<Capability> capabilities() {
        return capabilities;
    }

    public Set<SchedulerType> supportedSchedulers() {
        return supportedSchedulers;
    }

    public SchedulerType recommendedScheduler() {
        return recommendedScheduler;
    }

    public int maxResolution() {
        return maxResolution;
    }

    public int minSteps() {
        return minSteps;
    }

    public int maxSteps() {
        return maxSteps;
    }

    public boolean has(Capability capability) {
        Objects.requireNonNull(capability, "capability");
        return capabilities.contains(capability);
    }

    public boolean supportsScheduler(SchedulerType scheduler) {
        Objects.requireNonNull(scheduler, "scheduler");
        return supportedSchedulers.contains(scheduler);
    }

    /** Whether negative prompts are meaningful for this pipeline. */
    public boolean supportsNegativePrompt() {
        return capabilities.contains(Capability.NEGATIVE_PROMPT);
    }

    /** Whether CFG scaling is meaningful (false for distilled schedulers). */
    public boolean supportsCfg() {
        return capabilities.contains(Capability.CFG);
    }

    public static final class Builder {
        private final Set<Capability> capabilities = EnumSet.noneOf(Capability.class);
        private final Set<SchedulerType> supportedSchedulers = EnumSet.noneOf(SchedulerType.class);
        private SchedulerType recommendedScheduler;
        private int maxResolution = 2048;
        private int minSteps = 1;
        private int maxSteps = 100;

        public Builder capability(Capability c) {
            capabilities.add(c);
            return this;
        }

        public Builder capabilities(Capability... cs) {
            for (Capability c : cs) {
                capabilities.add(c);
            }
            return this;
        }

        public Builder scheduler(SchedulerType s) {
            supportedSchedulers.add(s);
            return this;
        }

        public Builder recommendedScheduler(SchedulerType s) {
            scheduler(s);
            this.recommendedScheduler = s;
            return this;
        }

        public Builder maxResolution(int maxResolution) {
            this.maxResolution = maxResolution;
            return this;
        }

        public Builder stepsRange(int min, int max) {
            this.minSteps = min;
            this.maxSteps = max;
            return this;
        }

        public PipelineCapabilities build() {
            if (recommendedScheduler == null) {
                for (SchedulerType s : supportedSchedulers) {
                    recommendedScheduler = s;
                    break;
                }
                if (recommendedScheduler == null) {
                    recommendedScheduler = SchedulerType.DDIM;
                }
            }
            return new PipelineCapabilities(this);
        }
    }
}