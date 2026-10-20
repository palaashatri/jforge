package atri.palaash.jforge.engine.scheduler;

import atri.palaash.jforge.api.SchedulerType;

import java.util.Arrays;

/**
 * DDIM scheduler for discrete alpha_cumprod schedules (SD 1.5 family).
 * Independent reusable engine object extracted from the monolith.
 */
public final class DdimScheduler implements Scheduler<FloatLatents> {

    private final float[] alphaCumprod;
    private final int trainTimesteps;

    /**
     * @param alphaCumprod alpha_cumprod schedule (ascending index = more noise)
     */
    public DdimScheduler(float[] alphaCumprod) {
        if (alphaCumprod == null || alphaCumprod.length == 0) {
            throw new IllegalArgumentException("alphaCumprod must be non-empty");
        }
        this.alphaCumprod = Arrays.copyOf(alphaCumprod, alphaCumprod.length);
        this.trainTimesteps = alphaCumprod.length;
    }

    /**
     * Convenience factory using the standard linear beta schedule.
     *
     * @param trainTimesteps number of training timesteps
     * @param betaStart      starting beta
     * @param betaEnd        ending beta
     * @return a DDIM scheduler with a computed schedule
     */
    public static DdimScheduler withLinearBeta(int trainTimesteps, double betaStart, double betaEnd) {
        return new DdimScheduler(SchedulerMath.computeAlphaCumprod(trainTimesteps, betaStart, betaEnd));
    }

    @Override
    public SchedulerType type() {
        return SchedulerType.DDIM;
    }

    @Override
    public int[] timesteps(int steps, int trainTimesteps) {
        return SchedulerMath.createTimesteps(steps, this.trainTimesteps);
    }

    @Override
    public float[] sigmas(int steps, int trainTimesteps) {
        int[] ts = timesteps(steps, trainTimesteps);
        float[] sigmas = new float[steps + 1];
        for (int i = 0; i < ts.length; i++) {
            sigmas[i] = SchedulerMath.alphaToSigma(alphaCumprod[Math.min(ts[i], alphaCumprod.length - 1)]);
        }
        sigmas[steps] = 0f;
        return sigmas;
    }

    /** alpha_cumprod at a given timestep index. */
    public float alphaAt(int timestep) {
        return alphaCumprod[Math.min(timestep, alphaCumprod.length - 1)];
    }

    @Override
    public FloatLatents step(FloatLatents latents, FloatLatents modelOutput, float sigma, float sigmaPrev) {
        // For DDIM we need alpha at the current and previous timesteps.
        // The implementation using SchedulerMath converts sigma back to alpha.
        float alphaT = sigmaToAlpha(sigma);
        float alphaPrev = sigmaToAlpha(sigmaPrev);
        float[][][][] out = SchedulerMath.ddimStep(
                latents.array(), modelOutput.array()[0], alphaT, alphaPrev);
        return FloatLatents.of(out);
    }

    private static float sigmaToAlpha(float sigma) {
        if (sigma <= 0) {
            return 1f;
        }
        return (float) (1.0 / (1.0 + sigma * sigma));
    }
}