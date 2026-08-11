package atri.palaash.jforge.engine.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerMathTest {

    @Test
    void ddimTimestepsAreDescendingAndBounded() {
        int[] ts = SchedulerMath.createTimesteps(20, 1000);
        assertEquals(20, ts.length);
        for (int i = 0; i < ts.length; i++) {
            assertTrue(ts[i] >= 0 && ts[i] < 1000);
            if (i > 0) {
                assertTrue(ts[i] < ts[i - 1], "timesteps must strictly descend");
            }
        }
    }

    @Test
    void alphaCumprodIsMonotonicDecreasing() {
        float[] alphas = SchedulerMath.computeAlphaCumprod(1000, 0.00085, 0.012);
        assertEquals(1000, alphas.length);
        assertTrue(alphas[0] < 1.0);
        for (int i = 1; i < alphas.length; i++) {
            assertTrue(alphas[i] < alphas[i - 1], "alpha_cumprod must decrease");
            assertTrue(alphas[i] > 0);
        }
    }

    @Test
    void turboTimestepsSpanAndDescend() {
        int[] ts = SchedulerMath.turboTimesteps(4);
        assertEquals(4, ts.length);
        assertEquals(999, ts[0]);
        assertEquals(0, ts[3]);
        assertTrue(ts[1] < ts[0]);
    }

    @Test
    void ddimStepProducesNoisyScale() {
        float[][][][] latents = new float[1][4][2][2];
        float[][][] eps = new float[4][2][2];
        latents[0][0][0][0] = 1.0f;
        eps[0][0][0] = 0.5f;
        float[][][][] out = SchedulerMath.ddimStep(latents, eps, 0.5f, 0.2f);
        assertEquals(1, out.length);
        assertEquals(4, out[0].length);
        assertEquals(2, out[0][0].length);
        assertEquals(2, out[0][0][0].length);
        // deterministic — same inputs produce identical output
        float[][][][] again = SchedulerMath.ddimStep(latents, eps, 0.5f, 0.2f);
        for (int c = 0; c < 4; c++) {
            for (int y = 0; y < 2; y++) {
                assertArrayEquals(out[0][c][y], again[0][c][y], 0.0f);
            }
        }
    }

    @Test
    void guidanceExtrapolatesTowardConditional() {
        float[][][] uncond = new float[1][1][1];
        float[][][] cond = new float[1][1][1];
        uncond[0][0][0] = 0.0f;
        cond[0][0][0] = 1.0f;
        float[][][] guided = SchedulerMath.guidance(uncond, cond, 7.5f);
        assertEquals(7.5f, guided[0][0][0], 1e-6f);
    }
}