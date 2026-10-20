package atri.palaash.jforge.engine.scheduler;

/**
 * Shared math helpers for noise schedules and step math. Pure functions,
 * no external state.
 */
public final class SchedulerMath {

    private SchedulerMath() {
    }

    /**
     * Load or compute a linear beta schedule alpha_cumprod array.
     *
     * @param trainTimesteps number of training timesteps (e.g. 1000)
     * @param betaStart      starting beta (e.g. 0.00085)
     * @param betaEnd        ending beta (e.g. 0.012)
     * @return alpha_cumprod[t] for t in [0, trainTimesteps)
     */
    public static float[] computeAlphaCumprod(int trainTimesteps, double betaStart, double betaEnd) {
        float[] alphaCumprod = new float[trainTimesteps];
        double cumulative = 1.0;
        for (int i = 0; i < trainTimesteps; i++) {
            double beta = betaStart + (betaEnd - betaStart) * i / Math.max(1, trainTimesteps - 1);
            cumulative *= (1.0 - beta);
            alphaCumprod[i] = (float) cumulative;
        }
        return alphaCumprod;
    }

    /**
     * Create an evenly-spaced schedule of timestep indices from
     * (trainTimesteps-1) down to 0, used by DDIM and similar schedulers.
     *
     * @param steps          number of steps
     * @param trainTimesteps total timesteps
     * @return descending timestep indices
     */
    public static int[] createTimesteps(int steps, int trainTimesteps) {
        int[] timesteps = new int[steps];
        float stride = (float) (trainTimesteps - 1) / Math.max(1, steps - 1);
        for (int i = 0; i < steps; i++) {
            timesteps[i] = Math.max(0, Math.round((steps - 1 - i) * stride));
        }
        return timesteps;
    }

    /**
     * Evenly-spaced timesteps for distilled schedules (999 → 0).
     *
     * @param steps number of steps
     * @return timestep indices
     */
    public static int[] turboTimesteps(int steps) {
        int[] ts = new int[steps];
        for (int i = 0; i < steps; i++) {
            ts[i] = (int) (999.0 * (steps - 1 - i) / Math.max(1, steps - 1));
        }
        if (steps == 1) {
            ts[0] = 999;
        }
        return ts;
    }

    /**
     * Approximate sigma for turbo schedules: sqrt((1-alpha_bar)/alpha_bar).
     *
     * @param timestep current timestep (0–999)
     * @return sigma value
     */
    public static float turboSigma(int timestep) {
        float t = timestep / 999.0f;
        float alphaBar = (float) Math.exp(-0.5 * t * t * 12.0);
        return (float) Math.sqrt((1 - alphaBar) / alphaBar);
    }

    /**
     * Convert an alpha_cumprod in [0,1] to sigma: sqrt((1 - alpha)/alpha).
     *
     * @param alphaCumprod cumulative alpha
     * @return sigma
     */
    public static float alphaToSigma(float alphaCumprod) {
        float safe = Math.max(1e-6f, Math.min(1f - 1e-6f, alphaCumprod));
        return (float) Math.sqrt((1.0 - safe) / safe);
    }

    /**
     * Element-wise multiplication of latents by a scalar.
     *
     * @param latents input latents [1][C][H][W]
     * @param scale   scalar multiplier
     * @return scaled latents
     */
    public static float[][][][] scaleLatents(float[][][][] latents, float scale) {
        int ch = latents[0].length, h = latents[0][0].length, w = latents[0][0][0].length;
        float[][][][] out = new float[1][ch][h][w];
        for (int c = 0; c < ch; c++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out[0][c][y][x] = latents[0][c][y][x] * scale;
                }
            }
        }
        return out;
    }

    /**
     * Duplicate a single sample into a batch of two (for CFG).
     *
     * @param latents the original [1][C][H][W]
     * @return [2][C][H][W]
     */
    public static float[][][][] duplicateBatch(float[][][][] latents) {
        int ch = latents[0].length, h = latents[0][0].length, w = latents[0][0][0].length;
        float[][][][] out = new float[2][ch][h][w];
        for (int c = 0; c < ch; c++) {
            for (int y = 0; y < h; y++) {
                System.arraycopy(latents[0][c][y], 0, out[0][c][y], 0, w);
                System.arraycopy(latents[0][c][y], 0, out[1][c][y], 0, w);
            }
        }
        return out;
    }

    /**
     * Classifier-free guidance: uncond + scale * (cond - uncond).
     *
     * @param uncond        unconditional model output
     * @param cond          conditional model output
     * @param guidanceScale CFG scale
     * @return guided output
     */
    public static float[][][] guidance(float[][][] uncond, float[][][] cond, float guidanceScale) {
        int c = uncond.length, h = uncond[0].length, w = uncond[0][0].length;
        float[][][] out = new float[c][h][w];
        for (int ch = 0; ch < c; ch++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out[ch][y][x] = uncond[ch][y][x] + guidanceScale * (cond[ch][y][x] - uncond[ch][y][x]);
                }
            }
        }
        return out;
    }

    /**
     * Single DDIM step with provided alpha_cumprod values.
     *
     * @param latents   current latents [1][C][H][W]
     * @param eps       predicted noise
     * @param alphaT    alpha_cumprod at current timestep
     * @param alphaPrev alpha_cumprod at previous timestep
     * @return updated latents
     */
    public static float[][][][] ddimStep(float[][][][] latents, float[][][] eps,
                                         float alphaT, float alphaPrev) {
        int c = latents[0].length, h = latents[0][0].length, w = latents[0][0][0].length;
        float sqrtAlphaT = (float) Math.sqrt(Math.max(1e-6f, alphaT));
        float sqrtOneMinusAlphaT = (float) Math.sqrt(Math.max(1e-6f, 1f - alphaT));
        float sqrtAlphaPrev = (float) Math.sqrt(Math.max(1e-6f, alphaPrev));
        float sqrtOneMinusAlphaPrev = (float) Math.sqrt(Math.max(1e-6f, 1f - alphaPrev));

        float[][][][] out = new float[1][c][h][w];
        for (int ch = 0; ch < c; ch++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float xT = latents[0][ch][y][x];
                    float e = eps[ch][y][x];
                    float x0 = (xT - sqrtOneMinusAlphaT * e) / sqrtAlphaT;
                    out[0][ch][y][x] = sqrtAlphaPrev * x0 + sqrtOneMinusAlphaPrev * e;
                }
            }
        }
        return out;
    }

    /**
     * Single Euler step (in sigma space): x + (sigmaPrev - sigma) * noise.
     *
     * @param latents   current latents [1][C][H][W]
     * @param noisePred model output
     * @param sigma     current sigma
     * @param sigmaPrev next sigma
     * @return updated latents
     */
    public static float[][][][] eulerStep(float[][][][] latents, float[][][] noisePred,
                                          float sigma, float sigmaPrev) {
        int ch = latents[0].length, h = latents[0][0].length, w = latents[0][0][0].length;
        float[][][][] out = new float[1][ch][h][w];
        float dt = sigmaPrev - sigma;
        for (int c = 0; c < ch; c++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    out[0][c][y][x] = latents[0][c][y][x] + dt * noisePred[c][y][x];
                }
            }
        }
        return out;
    }
}