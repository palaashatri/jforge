package atri.palaash.jforge.ui;

import java.time.LocalDateTime;

/**
 * A single row in the generation history — like a receipt for a completed image job.
 * <p>
 * Each entry records when the run happened, which model was used, the prompts,
 * random seed, batch size, resolution, style, job status, and where the
 * output file lives on disk.
 *
 * @param timestamp      When the generation was submitted or completed
 * @param model          The name or ID of the model that produced the output
 * @param prompt         The main prompt text that was sent to the model
 * @param negativePrompt The negative prompt text that was sent to the model
 * @param seed           The random seed used so the result can be re-created
 * @param batch          The batch size (how many images were requested at once)
 * @param size           The output resolution string, e.g. "1024x1024"
 * @param style          The style preset identifier that was applied
 * @param status         The outcome of the job (e.g. "success", "failed")
 * @param outputPath     The file-system path where the generated image(s) are stored
 */
public record HistoryEntry(
        LocalDateTime timestamp,
        String model,
        String prompt,
        String negativePrompt,
        long seed,
        int batch,
        String size,
        String style,
        String status,
        String outputPath
) {
}
