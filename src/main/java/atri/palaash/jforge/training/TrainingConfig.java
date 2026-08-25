package atri.palaash.jforge.training;

import java.nio.file.Path;

public record TrainingConfig(
        Path datasetDir,
        Path outputDir,
        String baseModelId,
        int resolution,
        int batchSize,
        int gradientAccumulation,
        double learningRate,
        String optimizer,
        int epochs,
        int stepsPerEpoch,
        boolean mixedPrecision,
        int checkpointEvery
) {
    public TrainingConfig {
        if (resolution < 64 || resolution > 2048) throw new IllegalArgumentException("resolution out of range");
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >=1");
        if (learningRate <= 0) throw new IllegalArgumentException("learningRate must be >0");
    }

    public static TrainingConfig defaults(Path datasetDir, Path outputDir, String baseModelId) {
        return new TrainingConfig(datasetDir, outputDir, baseModelId, 512, 1, 4, 1e-4, "adamw", 10, 100, true, 500);
    }

    public int totalSteps() { return epochs * stepsPerEpoch; }
}
