package atri.palaash.jforge.engine.random;

import java.util.Random;

/**
 * Deterministic latent-noise generation. Given the same seed, channel
 * count, and latent size, this always produces the identical noise tensor
 * on the same JVM/Zing implementation — the reproducibility foundation for
 * seeded generation.
 */
public final class LatentNoise {

    private LatentNoise() {
    }

    /**
     * Create a 4-channel Gaussian latent tensor {@code [1][4][H][W]}.
     *
     * @param seed         the seed (must be explicit; use {@link #freshSeed()} for random)
     * @param latentHeight latent height (image height / 8)
     * @param latentWidth  latent width (image width / 8)
     * @return noise tensor
     */
    public static float[][][][] randomLatents(long seed, int latentHeight, int latentWidth) {
        return randomLatents(seed, 4, latentHeight, latentWidth);
    }

    /**
     * Create a Gaussian latent tensor with an explicit channel count
     * (4 for SD/SDXL, 16 for SD 3.x).
     *
     * @param seed         the seed
     * @param channels     number of latent channels
     * @param latentHeight latent height
     * @param latentWidth  latent width
     * @return noise tensor
     */
    public static float[][][][] randomLatents(long seed, int channels, int latentHeight, int latentWidth) {
        if (channels < 1) {
            throw new IllegalArgumentException("channels must be >= 1");
        }
        Random random = new Random(seed);
        float[][][][] values = new float[1][channels][latentHeight][latentWidth];
        for (int c = 0; c < channels; c++) {
            for (int y = 0; y < latentHeight; y++) {
                for (int x = 0; x < latentWidth; x++) {
                    values[0][c][y][x] = (float) random.nextGaussian();
                }
            }
        }
        return values;
    }

    /**
     * Create a fresh unpredictable seed.
     *
     * @return a random long seed
     */
    public static long freshSeed() {
        return java.util.concurrent.ThreadLocalRandom.current().nextLong();
    }
}