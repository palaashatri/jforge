package atri.palaash.jforge.engine.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-computed golden values for scheduler math. These pin the exact
 * numeric behaviour of the schedule and step formulas (not just shape /
 * monotonicity), so an accidental change to a formula surfaces as a test
 * failure instead of silently changing outputs.
 */
class SchedulerGoldenTest {

    @Test
    void alphaCumprodMatchesHandComputedLinearSchedule() {
        // beta_i = 0.01 + (0.03 - 0.01) * i / (5 - 1)
        // alpha_cumprod[i] = product_{j<=i} (1 - beta_j)
        float[] got = SchedulerMath.computeAlphaCumprod(5, 0.01, 0.03);
        float[] expected = {0.99f, 0.97515f, 0.955647f, 0.9317558f, 0.9038031f};
        assertArrayEquals(expected, got, 1e-6f);
    }

    @Test
    void createTimestepsMatchesHandComputedStride() {
        // stride = 999 / 3 = 333
        assertArrayEquals(new int[]{999, 666, 333, 0}, SchedulerMath.createTimesteps(4, 1000));
        // stride = 999 / 2 = 499.5 → round(1 * 499.5) = 500
        assertArrayEquals(new int[]{999, 500, 0}, SchedulerMath.createTimesteps(3, 1000));
    }

    @Test
    void turboTimestepsMatchesHandComputedValues() {
        assertArrayEquals(new int[]{999, 666, 333, 0}, SchedulerMath.turboTimesteps(4));
        assertArrayEquals(new int[]{999, 0}, SchedulerMath.turboTimesteps(2));
        assertArrayEquals(new int[]{999}, SchedulerMath.turboTimesteps(1));
    }

    @Test
    void turboSigmaMatchesHandComputedProfile() {
        assertEquals(0.0f, SchedulerMath.turboSigma(0), 1e-6f);
        // t = 1.0 → alpha_bar = exp(-6) ≈ 0.00247875 → sigma = sqrt((1-a)/a) ≈ 20.0606
        assertEquals(20.0606f, SchedulerMath.turboSigma(999), 1e-3f);
        // t = 500/999 ≈ 0.5005005 → alpha_bar ≈ 0.2224608 → sigma ≈ 1.8695
        assertEquals(1.8695f, SchedulerMath.turboSigma(500), 1e-3f);
    }

    @Test
    void alphaToSigmaGoldenValues() {
        assertEquals(1.0f, SchedulerMath.alphaToSigma(0.5f), 1e-6f);
        // sqrt((1-0.25)/0.25) = sqrt(3)
        assertEquals((float) Math.sqrt(3.0), SchedulerMath.alphaToSigma(0.25f), 1e-5f);
    }

    @Test
    void eulerStepGoldenValue() {
        // x + (sigmaPrev - sigma) * noise = 2 + (0.5 - 1.0) * 0.5 = 1.75
        float[][][][] latents = {{{{2.0f}}}};
        float[][][] noise = {{{0.5f}}};
        float[][][][] out = SchedulerMath.eulerStep(latents, noise, 1.0f, 0.5f);
        assertEquals(1.75f, out[0][0][0][0], 1e-6f);
    }

    @Test
    void ddimStepGoldenValue() {
        // alphaT = 0.64, alphaPrev = 0.16:
        // x0 = (2 - sqrt(0.36) * 0.5) / sqrt(0.64) = 2.125
        // out = sqrt(0.16) * 2.125 + sqrt(0.84) * 0.5 ≈ 1.3082576
        float[][][][] latents = {{{{2.0f}}}};
        float[][][] eps = {{{0.5f}}};
        float[][][][] out = SchedulerMath.ddimStep(latents, eps, 0.64f, 0.16f);
        assertEquals(1.3082576f, out[0][0][0][0], 1e-4f);
    }

    @Test
    void guidanceGoldenValue() {
        // uncond + 7.5 * (cond - uncond) = 1 + 7.5 * (3 - 1) = 16
        float[][][] uncond = {{{1.0f}}};
        float[][][] cond = {{{3.0f}}};
        assertEquals(16.0f, SchedulerMath.guidance(uncond, cond, 7.5f)[0][0][0], 1e-6f);
    }

    @Test
    void scaleLatentsGoldenValue() {
        float[][][][] latents = {{{{2.0f, 4.0f}}}};
        float[][][][] out = SchedulerMath.scaleLatents(latents, 0.5f);
        assertEquals(1, out[0].length);
        assertEquals(1, out[0][0].length);
        assertEquals(2, out[0][0][0].length);
        assertEquals(1.0f, out[0][0][0][0], 1e-6f);
        assertEquals(2.0f, out[0][0][0][1], 1e-6f);
        // input must not be mutated (scaleLatents returns a fresh tensor)
        assertEquals(2.0f, latents[0][0][0][0], 1e-6f);
        assertEquals(4.0f, latents[0][0][0][1], 1e-6f);
    }

    @Test
    void flowMatchSigmasHandComputed() {
        // shift 3.0: sigma(t) = 3t / (1 + 2t) for t in {1, 3/4, 1/2, 1/4, 0}
        // → {1, 0.9, 0.75, 0.5, 0}
        FlowMatchEulerScheduler s = FlowMatchEulerScheduler.defaultShift();
        float[] got = s.sigmas(4, 1000);
        float[] expected = {1.0f, 0.9f, 0.75f, 0.5f, 0.0f};
        assertArrayEquals(expected, got, 1e-5f);
    }

    @Test
    void ddimSchedulerSigmasAndStepGoldenValues() {
        DdimScheduler s = new DdimScheduler(new float[]{0.64f, 0.16f});
        // timesteps(2, 2) = [1, 0]; sigmas from alpha_to_sigma
        float[] sigmas = s.sigmas(2, 2);
        float[] expectedSigmas = {
                (float) Math.sqrt(0.84 / 0.16), // sqrt(5.25) ≈ 2.2912878
                0.75f,                          // sqrt(0.36 / 0.64)
                0.0f
        };
        assertArrayEquals(expectedSigmas, sigmas, 1e-5f);

        // step with sigma 0.75 → alpha 0.64 and sigmaPrev 0.5 → alpha 0.8
        FloatLatents out = s.step(latentConstant(2.0f), latentConstant(0.5f), 0.75f, 0.5f);
        // sqrt(0.8)*2.125 + sqrt(0.2)*0.5 ≈ 2.1242646
        assertEquals(2.1242646f, out.array()[0][0][0][0], 1e-4f);
    }

    @Test
    void eulerAndDistilledSchedulerGoldenValues() {
        EulerScheduler euler = EulerScheduler.discrete();
        FloatLatents out = euler.step(latentConstant(2.0f), latentConstant(0.5f), 1.0f, 0.75f);
        // 2 + (0.75 - 1.0) * 0.5 = 1.875
        assertEquals(1.875f, out.array()[0][0][0][0], 1e-6f);

        DistilledEulerScheduler distilled = new DistilledEulerScheduler();
        float[] sigmas = distilled.sigmas(2, 1000);
        assertEquals(20.0606f, sigmas[0], 1e-3f);
        assertEquals(0.0f, sigmas[1], 1e-6f);
        assertEquals(0.0f, sigmas[2], 1e-6f);
    }

    private static FloatLatents latentConstant(float value) {
        float[][][][] data = new float[1][1][1][1];
        data[0][0][0][0] = value;
        return FloatLatents.of(data);
    }
}