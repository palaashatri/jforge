package atri.palaash.jforge.training;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TrainingWorkspace {
    private final TrainingConfig config;
    private final Path workspaceDir;
    private final List<TrainingCheckpoint> checkpoints = new ArrayList<>();
    private int currentStep = 0;
    private double lastLoss = Double.NaN;

    public record TrainingCheckpoint(int step, double loss, Path path, Instant timestamp) {}

    public TrainingWorkspace(TrainingConfig config) {
        this.config = config;
        this.workspaceDir = config.outputDir();
    }

    public TrainingConfig config() { return config; }
    public int currentStep() { return currentStep; }
    public double lastLoss() { return lastLoss; }
    public List<TrainingCheckpoint> checkpoints() { return List.copyOf(checkpoints); }

    public void recordStep(int step, double loss) {
        this.currentStep = step;
        this.lastLoss = loss;
        if (step % config.checkpointEvery() == 0) {
            Path cp = workspaceDir.resolve("checkpoint-" + step);
            checkpoints.add(new TrainingCheckpoint(step, loss, cp, Instant.now()));
        }
    }

    public TrainingCheckpoint latestCheckpoint() {
        return checkpoints.isEmpty() ? null : checkpoints.get(checkpoints.size()-1);
    }

    public boolean canResume() { return !checkpoints.isEmpty(); }

    public void saveCheckpoint() throws IOException {
        Files.createDirectories(workspaceDir);
        Path marker = workspaceDir.resolve("training.json");
        String json = String.format("{\"step\":%d,\"loss\":%s,\"checkpoints\":%d}", currentStep, lastLoss, checkpoints.size());
        Files.writeString(marker, json);
    }

    public static TrainingWorkspace resume(Path workspaceDir) throws IOException {
        Path marker = workspaceDir.resolve("training.json");
        if (!Files.exists(marker)) throw new IOException("No training state found in " + workspaceDir);
        String json = Files.readString(marker);
        int step = 0;
        try { step = Integer.parseInt(json.replaceAll(".*\"step\":(\\d+).*", "$1")); } catch (Exception ignored) {}
        TrainingConfig cfg = TrainingConfig.defaults(workspaceDir.resolve("dataset"), workspaceDir, "unknown");
        TrainingWorkspace ws = new TrainingWorkspace(cfg);
        ws.currentStep = step;
        return ws;
    }
}
