package atri.palaash.jforge.engine.scheduler;

import atri.palaash.jforge.api.SchedulerType;

/**
 * Flow-matching Euler scheduler for SD 3.x / transformer-family models
 * (16-channel latents, shifted sigma schedule).
 */
public final class FlowMatchEulerScheduler implements Scheduler<FloatLatents> {

    private final float shift;

    /**
     * @param shift schedule shift factor (e.g. 3.0 for SD 3.5 medium)
     */
    public FlowMatchEulerScheduler(float shift) {
        this.shift = shift;
    }

    public static FlowMatchEulerScheduler defaultShift() {
        return new FlowMatchEulerScheduler(3.0f);
    }

    @Override
    public SchedulerType type() {
        return SchedulerType.FLOW_MATCH_EULER;
    }

    @Override
    public int[] timesteps(int steps, int trainTimesteps) {
        // Flow matching uses sigma positions 1.0 → 0.0; timestep indices are
        // informational for this schedule.
        int[] ts = new int[steps];
        for (int i = 0; i < steps; i++) {
            float t = 1.0f - (float) i / steps;
            ts[i] = Math.max(0, Math.round(t * (trainTimesteps - 1)));
        }
        return ts;
    }

    @Override
    public float[] sigmas(int steps, int trainTimesteps) {
        float[] sigmas = new float[steps + 1];
        for (int i = 0; i <= steps; i++) {
            float t = 1.0f - (float) i / steps;
            sigmas[i] = shifted(t);
        }
        return sigmas;
    }

    /** Shifted flow-matching sigma at time t in [0,1]. */
    public float shifted(float t) {
        return shift * t / (1.0f + (shift - 1.0f) * t);
    }

    @Override
    public FloatLatents step(FloatLatents latents, FloatLatents modelOutput, float sigma, float sigmaPrev) {
        float[][][][] out = SchedulerMath.eulerStep(latents.array(), modelOutput.array()[0], sigma, sigmaPrev);
        return FloatLatents.of(out);
    }
}