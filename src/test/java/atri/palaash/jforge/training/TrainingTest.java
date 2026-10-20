package atri.palaash.jforge.training;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TrainingTest {
    @Test void datasetValidationDetectsMissingImages(@TempDir Path tmp) {
        var r = DatasetValidator.validate(tmp);
        assertFalse(r.valid());
        assertTrue(r.imageCount() == 0);
    }
    @Test void datasetValidationCountsImagesAndCaptions(@TempDir Path tmp) throws Exception {
        Files.write(tmp.resolve("a.png"), new byte[10]);
        Files.writeString(tmp.resolve("a.txt"), "a cat");
        Files.write(tmp.resolve("b.jpg"), new byte[10]);
        var r = DatasetValidator.validate(tmp);
        assertEquals(2, r.imageCount());
        assertEquals(1, r.captionCount());
        assertFalse(r.valid());
        assertTrue(r.errors().stream().anyMatch(e -> e.contains("Missing captions")));
    }
    @Test void trainingConfigValidation() {
        assertThrows(IllegalArgumentException.class, () -> new TrainingConfig(null, Path.of("out"), "m", 32, 1, 1, 1e-4, "adamw", 1, 1, true, 100));
        TrainingConfig c = TrainingConfig.defaults(Path.of("data"), Path.of("out"), "sd15");
        assertEquals(10*100, c.totalSteps());
    }
    @Test void workspaceCheckpointAndResume(@TempDir Path tmp) throws Exception {
        TrainingConfig cfg = new TrainingConfig(tmp.resolve("data"), tmp.resolve("out"), "sd15", 512, 1, 4, 1e-4, "adamw", 2, 10, true, 5);
        Files.createDirectories(cfg.datasetDir());
        TrainingWorkspace ws = new TrainingWorkspace(cfg);
        ws.recordStep(5, 0.5);
        ws.recordStep(10, 0.3);
        assertEquals(2, ws.checkpoints().size());
        assertTrue(ws.canResume());
        ws.saveCheckpoint();
        assertTrue(Files.exists(tmp.resolve("out").resolve("training.json")));
        TrainingWorkspace resumed = TrainingWorkspace.resume(tmp.resolve("out"));
        assertEquals(10, resumed.currentStep());
    }
}
