package atri.palaash.jforge.inference;

import atri.palaash.jforge.model.ModelDescriptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A complete description of one inference job that jForge should run.
 * <p>
 * Think of this as an order form you hand to a kitchen: it says which
 * model to use, what text prompt to cook with, how many servings (batch
 * size) to make, what dimensions the output image should have, and
 * whether you want the result upscaled.  It also carries a progress
 * callback so the UI can show status messages, and a cancellation flag
 * so the user can change their mind mid-way.
 */
public record InferenceRequest(
	/** The model to run inference with (determines which pipeline is loaded). */
	ModelDescriptor model,
	/** The primary text description of the desired image ("what to draw"). */
	String prompt,
	/** Text describing things to avoid in the output ("what NOT to draw"). */
	String negativePrompt,
	/** How strongly to follow the prompt vs. letting the model be creative (CFG scale). */
	double promptWeight,
	/** Random-number seed for reproducible results. Same seed + same inputs = same image. */
	long seed,
	/** Number of images to generate in one run. */
	int batch,
	/** Output image width in pixels. */
	int width,
	/** Output image height in pixels. */
	int height,
	/**
	 * Optional style preset (e.g. "anime", "photorealistic").
	 * May be empty string if no style is selected.
	 */
	String style,
	/** Whether to run an upscaling pass after generating the initial image. */
	boolean upscale,
	/** Path to an existing input image (used for img2img or upscaling). Empty string for text-to-image. */
	String inputImagePath,
	/** If true, attempt to run on GPU; otherwise force CPU execution. */
	boolean preferGpu,
	/** Callback invoked with status messages during inference (e.g. "Loading model… 50%"). May be null. */
	Consumer<String> progressCallback,
	/** Shared flag that, when set to true, signals the running inference to stop early. May be null. */
	AtomicBoolean cancellationFlag
) {
	/**
	 * Convenience constructor that omits both the progress callback and the
	 * cancellation flag.  Provided for backward compatibility with callers
	 * that do not need progress reporting or cancellation support.
	 *
	 * @param model           the model to use
	 * @param prompt          primary text prompt
	 * @param negativePrompt  negative / undesired prompt
	 * @param promptWeight    CFG scale weight
	 * @param seed            random seed for reproducibility
	 * @param batch           number of images to generate
	 * @param width           output width in pixels
	 * @param height          output height in pixels
	 * @param style           style preset (may be empty)
	 * @param upscale         whether to run upscaling after generation
	 * @param inputImagePath  path to input image for img2img / upscale
	 * @param preferGpu       whether to prefer GPU execution
	 */
	public InferenceRequest(
			ModelDescriptor model, String prompt, String negativePrompt,
			double promptWeight, long seed, int batch,
			int width, int height, String style, boolean upscale,
			String inputImagePath, boolean preferGpu) {
		this(model, prompt, negativePrompt, promptWeight, seed, batch,
				width, height, style, upscale, inputImagePath, preferGpu, null, null);
	}

	/**
	 * Convenience constructor that accepts a progress callback but no
	 * cancellation flag.  Useful when you want status updates but do
	 * not need the ability to abort the running inference.
	 *
	 * @param model             the model to use
	 * @param prompt            primary text prompt
	 * @param negativePrompt    negative / undesired prompt
	 * @param promptWeight      CFG scale weight
	 * @param seed              random seed for reproducibility
	 * @param batch             number of images to generate
	 * @param width             output width in pixels
	 * @param height            output height in pixels
	 * @param style             style preset (may be empty)
	 * @param upscale           whether to run upscaling after generation
	 * @param inputImagePath    path to input image for img2img / upscale
	 * @param preferGpu         whether to prefer GPU execution
	 * @param progressCallback  consumer that receives progress messages (may be null)
	 */
	public InferenceRequest(
			ModelDescriptor model, String prompt, String negativePrompt,
			double promptWeight, long seed, int batch,
			int width, int height, String style, boolean upscale,
			String inputImagePath, boolean preferGpu,
			Consumer<String> progressCallback) {
		this(model, prompt, negativePrompt, promptWeight, seed, batch,
				width, height, style, upscale, inputImagePath, preferGpu,
				progressCallback, null);
	}

	/**
	 * Sends a progress message to the registered callback, if one exists.
	 * <p>
	 * This is a no-op when no {@code progressCallback} was provided —
	 * safe to call unconditionally.
	 *
	 * @param message the progress text to report (e.g. "Step 12 of 50")
	 */
	public void reportProgress(String message) {
		if (progressCallback != null) {
			progressCallback.accept(message);
		}
	}

	/**
	 * Checks whether the user (or some other component) has requested
	 * that this inference job be cancelled.
	 * <p>
	 * If no cancellation flag was provided, this always returns {@code false}.
	 *
	 * @return {@code true} if cancellation has been requested
	 */
	public boolean isCancelled() {
		return cancellationFlag != null && cancellationFlag.get();
	}
}
