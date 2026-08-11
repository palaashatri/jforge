package atri.palaash.jforge.engine.scheduler;

import atri.palaash.jforge.api.SchedulerType;

/**
 * Distilled single/multi-step Euler scheduler used by turbo models
 * (SD Turbo / SDXL Turbo): 1–8 steps, no CFG, roughly linear sigma space.
 */
public final class DistilledEulerScheduler implements Scheduler<FloatLatents> {

    public static final int MAX_STEPS = 8;
    public static final int MIN_STEPS = 1;

    @Override
    public SchedulerType type() {
        return SchedulerType.DISTILLED_EULER;
    }

    @Override
    public int[] timesteps(int steps, int trainTimesteps) {
        return SchedulerMath.turboTimesteps(steps);
    }

    @Override
    public float[] sigmas(int steps, int trainTimesteps) {
        int[] ts = timesteps(steps, trainTimesteps);
        float[] sigmas = new float[steps + 1];
        for (int i = 0; i < ts.length; i++) {
            sigmas[i] = SchedulerMath.turboSigma(ts[i]);
        }
        sigmas[steps] = 0f;
        return sigmas;
    }

    @Override
    public FloatLatents step(FloatLatents latents, FloatLatents modelOutput, float sigma, float sigmaPrev) {
        float[][][][] out = SchedulerMath.eulerStep(latents.array(), modelOutput.array()[0], sigma, sigmaPrev);
        return FloatLatents.of(out);
    }

    /** Clamp a requested step count into the supported distilled range. */
    public int clampSteps(int steps) {
        return Math.max(MIN_STEPS, Math.min(MAX_STEPS, steps));
    }
}