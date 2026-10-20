package atri.palaash.jforge.training;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class DatasetValidator {
    public record ValidationResult(boolean valid, List<String> errors, int imageCount, int captionCount) {
        public boolean hasErrors() { return !errors.isEmpty(); }
    }

    public static ValidationResult validate(Path datasetDir) {
        List<String> errors = new ArrayList<>();
        if (datasetDir == null || !Files.isDirectory(datasetDir)) {
            errors.add("Dataset directory does not exist: " + datasetDir);
            return new ValidationResult(false, errors, 0, 0);
        }
        try (Stream<Path> files = Files.list(datasetDir)) {
            List<Path> images = files.filter(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp");
            }).toList();
            int captions = 0;
            for (Path img : images) {
                String base = img.getFileName().toString().replaceFirst("\\.[^.]+$", "");
                Path txt = datasetDir.resolve(base + ".txt");
                Path caption = datasetDir.resolve(base + ".caption");
                if (Files.exists(txt) || Files.exists(caption)) captions++;
            }
            if (images.isEmpty()) errors.add("No images found in dataset directory");
            else if (captions == 0) errors.add("No caption files (.txt) found alongside images");
            else if (captions < images.size()) errors.add("Missing captions for " + (images.size() - captions) + " images");
            return new ValidationResult(errors.isEmpty(), errors, images.size(), captions);
        } catch (IOException e) {
            errors.add("Failed to read dataset: " + e.getMessage());
            return new ValidationResult(false, errors, 0, 0);
        }
    }
}
