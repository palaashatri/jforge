package atri.palaash.jforge.engine.scheduler;

import atri.palaash.jforge.api.SchedulerType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerTest {

    @Test
    void ddimSchedulerTypesAndSteps() {
        DdimScheduler s = DdimScheduler.withLinearBeta(1000, 0.00085, 0.012);
        assertEquals(SchedulerType.DDIM, s.type());
        int[] ts = s.timesteps(20, 1000);
        assertEquals(20, ts.length);
        assertTrue(ts[0] > ts[ts.length - 1]);
    }

    @Test
    void ddimStepRuns() {
        DdimScheduler s = DdimScheduler.withLinearBeta(1000, 0.00085, 0.012);
        FloatLatents latents = FloatLatents.of(new float[1][4][2][2]);
        FloatLatents out = s.step(latents, latents, 1.0f, 0.8f);
        assertEquals(4, out.channels());
        assertEquals(2, out.height());
        assertEquals(2, out.width());
    }

    @Test
    void flowMatchSigmasDescendAndEndAtZero() {
        FlowMatchEulerScheduler s = FlowMatchEulerScheduler.defaultShift();
        float[] sigmas = s.sigmas(10, 1000);
        assertEquals(11, sigmas.length);
        assertEquals(1.0f, sigmas[0], 1e-6f);
        assertEquals(0.0f, sigmas[10], 1e-6f);
        for (int i = 1; i < sigmas.length; i++) {
            assertTrue(sigmas[i] < sigmas[i - 1] + 1e-6f, "sigmas must descend");
        }
    }

    @Test
    void flowMatchShiftedBounds() {
        FlowMatchEulerScheduler s = FlowMatchEulerScheduler.defaultShift();
        assertEquals(0.0f, s.shifted(0f), 1e-6f);
        assertEquals(1.0f, s.shifted(1f), 1e-6f);
        // midpoint with shift 3 is closer to 1 than 0 (spends more time near full noise)
        float mid = s.shifted(0.5f);
        assertTrue(mid > 0.5f);
    }

    @Test
    void distilledClampsSteps() {
        DistilledEulerScheduler s = new DistilledEulerScheduler();
        assertEquals(1, s.clampSteps(0));
        assertEquals(8, s.clampSteps(99));
        assertEquals(4, s.clampSteps(4));
        assertEquals(SchedulerType.DISTILLED_EULER, s.type());
    }

    @Test
    void eulerStepDeterministic() {
        EulerScheduler s = EulerScheduler.discrete();
        FloatLatents latents = FloatLatents.of(new float[1][4][3][3]);
        FloatLatents out1 = s.step(latents, latents, 1.0f, 0.5f);
        FloatLatents out2 = s.step(latents, latents, 1.0f, 0.5f);
        for (int c = 0; c < 4; c++) {
            for (int y = 0; y < 3; y++) {
                assertEquals(out1.array()[0][c][y][0], out2.array()[0][c][y][0], 0.0f);
            }
        }
    }
}