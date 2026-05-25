package atri.palaash.jforge.ui;

/**
 * A saved prompt preset — like a bookmark for your favourite image-generation inputs.
 * <p>
 * Each preset bundles a name, the main prompt, an optional negative prompt,
 * search-able tags, and a style selector.  Users can save, search, delete, or
 * apply presets through the library panel.
 *
 * @param name           The human-readable label for this preset
 * @param prompt         The primary text describing what to generate
 * @param negativePrompt Text describing what the generator should avoid
 * @param tags           Space- or comma-separated keywords used for filtering
 * @param style          Identifier for the visual style (e.g. "anime", "realistic")
 */
public record PromptPreset(
        String name,
        String prompt,
        String negativePrompt,
        String tags,
        String style
) {
}
