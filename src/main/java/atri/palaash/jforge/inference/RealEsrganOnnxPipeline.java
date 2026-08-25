package atri.palaash.jforge.inference;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import atri.palaash.jforge.storage.ModelStorage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

/**
 * Real-ESRGAN ONNX upscaling pipeline. Tiles large images to fit the model's
 * fixed input size, runs multi-pass upscaling, and resizes to target dimensions.
 */
class RealEsrganOnnxPipeline extends OnnxPipelineBase {

    RealEsrganOnnxPipeline(ModelStorage storage) {
        super(storage);
    }

    /**
     * Run the Real-ESRGAN upscaling pipeline.
     * <p>
     * Loads an input image, determines the model's native scale factor via a probe
     * inference, optionally performs multi-pass upscaling, tiles large images to
     * fit the model's fixed input size, and finally resizes to the target dimensions.
     * </p>
     *
     * @param environment shared ONNX Runtime environment
     * @param session     a loaded ONNX session for the Real-ESRGAN model
     * @param request     the user's upscale request (input image, target size, style)
     * @param provider    execution provider display name
     * @return the result containing the upscaled image path, or a failure message
     */
    InferenceResult run(OrtEnvironment environment, OrtSession session, InferenceRequest request, String provider) {
        String inputPath = request.inputImagePath();
        if (inputPath == null || inputPath.isBlank()) {
            return InferenceResult.fail("Real-ESRGAN requires an input image. Choose an image to upscale.");
        }

        try {
            BufferedImage inputImage = ImageIO.read(new File(inputPath));
            if (inputImage == null) {
                return InferenceResult.fail("Unable to read input image: " + inputPath);
            }

            // Determine the model's expected spatial input size from its metadata.
            String inputName = session.getInputNames().iterator().next();
            TensorInfo inputInfo = (TensorInfo) session.getInputInfo().get(inputName).getInfo();
            long[] inputShape = inputInfo.getShape();  // e.g. [1, 3, 64, 64]
            int tileH = (inputShape.length >= 3 && inputShape[2] > 0) ? (int) inputShape[2] : 0;
            int tileW = (inputShape.length >= 4 && inputShape[3] > 0) ? (int) inputShape[3] : 0;

            // Detect native scale factor.
            int scaleFactor = detectScaleFactor(environment, session, inputName, tileH, tileW);

            // Determine the resize method from the style field (default: Bicubic).
            String resizeMethod = (request.style() != null && !request.style().isBlank())
                    ? request.style() : "Bicubic";

            int imgW = inputImage.getWidth();
            int imgH = inputImage.getHeight();
            int targetW = request.width();
            int targetH = request.height();

            // Calculate how many native ESRGAN passes are needed.
            int passes = 1;
            if ("ESRGAN Multi-Pass".equals(resizeMethod) && targetW > 0 && targetH > 0) {
                int curW = imgW * scaleFactor;
                int curH = imgH * scaleFactor;
                while (curW < targetW || curH < targetH) {
                    curW *= scaleFactor;
                    curH *= scaleFactor;
                    passes++;
                }
            }

            // Run ESRGAN for each pass.
            BufferedImage current = inputImage;
            for (int pass = 0; pass < passes; pass++) {
                if (request.isCancelled()) {
                    return InferenceResult.fail("Cancelled by user.");
                }
                request.reportProgress("Upscaling pass " + (pass + 1) + "/" + passes
                        + " (" + current.getWidth() + "×" + current.getHeight() + " → "
                        + (current.getWidth() * scaleFactor) + "×" + (current.getHeight() * scaleFactor) + ")…");
                current = esrganUpscaleOnce(environment, session, current, inputName, tileH, tileW, scaleFactor);
            }

            // Final resize to exact target dimensions.
            String finalMethod = "ESRGAN Multi-Pass".equals(resizeMethod) ? "Bicubic" : resizeMethod;
            if (targetW > 0 && targetH > 0) {
                current = resizeImage(current, targetW, targetH, finalMethod);
            }

            Path outputPath = writeOutputImage(current, "realesrgan");
            return InferenceResult.ok(
                    "Real-ESRGAN upscale completed (" + scaleFactor + "× scale, " + passes + " pass"
                            + (passes > 1 ? "es" : "") + ", " + resizeMethod
                            + (targetW > 0 ? ", final " + current.getWidth() + "×" + current.getHeight() : "")
                            + ").",
                    "Java-native ONNX Runtime executed Real-ESRGAN | EP=" + provider,
                    outputPath.toString(),
                    "image"
            );
        } catch (Exception ex) {
            return InferenceResult.fail("Real-ESRGAN failed: " + ex.getMessage());
        }
    }
}
