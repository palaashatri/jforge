package atri.palaash.jforge.api;

import java.util.List;

/**
 * Outcome of a {@link GenerationRequest} executed by a pipeline.
 * <p>
 * On success {@link #success()} is true and {@link #images()} contains the
 * produced artifacts.  On failure {@link #error()} carries a
 * human-readable, actionable message (never a bare stack trace).
 */
public record GenerationResult(
        boolean success,
        String generationId,
        List<GeneratedImage> images,
        GenerationManifest manifest,
        String error,
        long elapsedMillis,
        String device,
        String backend
) {
    public GenerationResult {
        generationId = generationId == null ? "" : generationId;
        images = images == null ? List.of() : List.copyOf(images);
manifest = manifest == null ? GenerationManifest.emptyManifest() : manifest;
        error = error == null ? "" : error;
        device = device == null ? "" : device;
        backend = backend == null ? "" : backend;
    }

    public static GenerationResult ok(String generationId, List<GeneratedImage> images,
                                      GenerationManifest manifest, long elapsedMillis,
                                      String device, String backend) {
        return new GenerationResult(true, generationId, images, manifest, "",
                elapsedMillis, device, backend);
    }

    public static GenerationResult fail(String generationId, String error) {
        return new GenerationResult(false, generationId, List.of(), GenerationManifest.emptyManifest(), error,
                0L, "", "");
    }

    public static GenerationResult fail(String error) {
        return fail("", error);
    }
}