package atri.palaash.jforge.engine.scheduler;

import atri.palaash.jforge.api.SchedulerType;

/**
 * Euler discrete scheduler for SDXL / turbo-family models.
 * <p>
 * Uses the sigma-space Euler integration {@code x += (sigmaPrev - sigma) * v}.
 */
public final class EulerScheduler implements Scheduler<FloatLatents> {

    private final boolean ancestral;

    private EulerScheduler(boolean ancestral) {
        this.ancestral = ancestral;
    }

    /** Standard (non-ancestral) Euler. */
    public static EulerScheduler discrete() {
        return new EulerScheduler(false);
    }

    /** Ancestral variant (adds stochastic noise each step). */
    public static EulerScheduler ancestral() {
        return new EulerScheduler(true);
    }

    @Override
    public SchedulerType type() {
        return ancestral ? SchedulerType.EULER_ANCESTRAL : SchedulerType.EULER;
    }

    @Override
    public int[] timesteps(int steps, int trainTimesteps) {
        return SchedulerMath.createTimesteps(steps, trainTimesteps);
    }

    @Override
    public float[] sigmas(int steps, int trainTimesteps) {
        float[] alphas = SchedulerMath.computeAlphaCumprod(trainTimesteps, 0.00085, 0.012);
        int[] ts = timesteps(steps, trainTimesteps);
        float[] sigmas = new float[steps + 1];
        for (int i = 0; i < ts.length; i++) {
            sigmas[i] = SchedulerMath.alphaToSigma(alphas[Math.min(ts[i], alphas.length - 1)]);
        }
        sigmas[steps] = 0f;
        return sigmas;
    }

    @Override
    public FloatLatents step(FloatLatents latents, FloatLatents modelOutput, float sigma, float sigmaPrev) {
        float[][][][] out = SchedulerMath.eulerStep(latents.array(), modelOutput.array()[0], sigma, sigmaPrev);
        return FloatLatents.of(out);
    }
}