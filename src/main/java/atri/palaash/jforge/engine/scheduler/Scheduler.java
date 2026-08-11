package atri.palaash.jforge.engine.scheduler;

import atri.palaash.jforge.api.SchedulerType;

/**
 * Base contract for a denoising scheduler.
 * <p>
 * A scheduler owns the timestep/sigma schedule and the integration step
 * for a given model family. Instances are immutable once constructed and
 * usable from any thread. This is the engine-side abstraction that will
 * replace the inline math currently embedded in {@code GenericOnnxService}.
 *
 * @param <L> latent tensor type used by the scheduler (engine-specific)
 */
public interface Scheduler<L> {

    /** The scheduler type. */
    SchedulerType type();

    /**
     * Compute the timestep indices for a denoising run.
     *
     * @param steps          desired number of denoising steps
     * @param trainTimesteps the number of timesteps the model was trained with (e.g. 1000)
     * @return descending array of timestep indices
     */
    int[] timesteps(int steps, int trainTimesteps);

    /**
     * Compute sigma values corresponding to {@link #timesteps}. Implementation
     * may derive sigma from timesteps or define its own mapping.
     *
     * @param steps          desired number of denoising steps
     * @param trainTimesteps the number of timesteps the model was trained with
     * @return array of length {@code steps + 1} (last entry is final sigma)
     */
    float[] sigmas(int steps, int trainTimesteps);

    /**
     * Perform a single denoising step.
     *
     * @param latents      current latent sample
     * @param modelOutput  predicted noise/velocity for the current timestep
     * @param sigma         current noise level
     * @param sigmaPrev     next (earlier) noise level
     * @return the updated latent
     */
    L step(L latents, L modelOutput, float sigma, float sigmaPrev);
}