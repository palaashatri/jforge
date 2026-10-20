package atri.palaash.jforge.engine.random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatentNoiseTest {

    @Test
    void sameSeedProducesIdenticalLatents() {
        float[][][][] a = LatentNoise.randomLatents(42, 4, 8, 8);
        float[][][][] b = LatentNoise.randomLatents(42, 4, 8, 8);
        for (int c = 0; c < 4; c++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(a[0][c][y][x], b[0][c][y][x]);
                }
            }
        }
    }

    @Test
    void differentSeedsProduceDifferentLatents() {
        float[][][][] a = LatentNoise.randomLatents(1, 4, 8, 8);
        float[][][][] b = LatentNoise.randomLatents(2, 4, 8, 8);
        boolean differs = false;
        outer:
        for (int c = 0; c < 4; c++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (a[0][c][y][x] != b[0][c][y][x]) {
                        differs = true;
                        break outer;
                    }
                }
            }
        }
        assertTrue(differs, "different seeds should produce different noise");
    }

    @Test
    void channelCountRespected() {
        float[][][][] latents = LatentNoise.randomLatents(7, 16, 8, 8);
        assertEquals(16, latents[0].length);
    }
}