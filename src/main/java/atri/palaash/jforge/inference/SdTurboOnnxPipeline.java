package atri.palaash.jforge.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import atri.palaash.jforge.storage.ModelStorage;
import atri.palaash.jforge.tokenize.ClipTokenizer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SD Turbo ONNX pipeline — distilled 1–4 step pipeline (no classifier-free
 * guidance, uses an Euler-based single-step scheduler).
 */
class SdTurboOnnxPipeline extends OnnxPipelineBase {

    SdTurboOnnxPipeline(ModelStorage storage) {
        super(storage);
    }

    /**
     * Run the distilled SD Turbo pipeline (1–8 steps, no classifier-free guidance).
     * <p>
     * Uses an Euler-style single-step scheduler instead of DDIM. Much faster than
     * full SD v1.5 but produces lower-quality images that are still recognisable.
     * </p>
     *
     * @param environment   shared ONNX Runtime environment
     * @param sessionOptions session configuration
     * @param request       user's inference request
     * @param provider      execution provider display name
     * @return the result containing the generated image path, or a failure message
     */
    InferenceResult run(OrtEnvironment environment,
                        OrtSession.SessionOptions sessionOptions,
                        InferenceRequest request,
                        String provider) {
        try {
            int width  = Math.max(256, (request.width()  / 8) * 8);
            int height = Math.max(256, (request.height() / 8) * 8);
            int latentWidth  = width  / 8;
            int latentHeight = height / 8;
            int steps = Math.max(1, Math.min(request.batch() > 0 ? request.batch() : 4, 8));

            Path base = storage.root().resolve("text-image").resolve("sd-turbo");
            Path textEncoderPath = base.resolve("text_encoder/model.onnx");
            Path unetPath        = base.resolve("unet/model.onnx");
            Path vaeDecoderPath  = base.resolve("vae_decoder/model.onnx");

            // SD Turbo shares the same vocabulary as SD 1.x — reuse from SD v1.5 if present,
            // otherwise look for them inside the sd-turbo directory.
            Path vocabPath   = resolveTokenizerFile(base, "tokenizer/vocab.json");
            Path mergesPath  = resolveTokenizerFile(base, "tokenizer/merges.txt");

            for (Path p : List.of(textEncoderPath, unetPath, vaeDecoderPath, vocabPath, mergesPath)) {
                if (!java.nio.file.Files.exists(p)) {
                    return InferenceResult.fail("SD Turbo bundle is incomplete (missing " + p.getFileName()
                            + "). Open Models → Model Manager and download all SD Turbo components. "
                            + "Tokenizer files also need to be present.");
                }
            }

            ClipTokenizer tokenizer = getOrCreateTokenizer(vocabPath, mergesPath);
            long[] promptTokens = tokenizer.encode(request.prompt(), 77);

            request.reportProgress("Loading SD Turbo models (text encoder, UNet, VAE decoder)…");
            OrtSession textEncoder = getOrCreateSession(environment, textEncoderPath, sessionOptions);
            OrtSession unet = getOrCreateSession(environment, unetPath, sessionOptions);
            OrtSession vaeDecoder = getOrCreateSession(environment, vaeDecoderPath, sessionOptions);
            {

                request.reportProgress("Encoding text prompt…");
                float[][][] textEmbeddings = runTextEncoder(environment, textEncoder, promptTokens);

                // SD Turbo does NOT use classifier-free guidance — single batch only.
                float[][][][] latents = randomLatents(request.seed(), latentHeight, latentWidth);

                // Euler-style timestep schedule for turbo distillation.
                int[] timesteps = turboTimesteps(steps);

                request.reportProgress("Denoising: 0/" + steps + " steps (SD Turbo) — EP: " + provider);
                long stepStart = System.currentTimeMillis();
                for (int i = 0; i < timesteps.length; i++) {
                    int t = timesteps[i];

                    OnnxTensor sampleTensor   = OnnxTensor.createTensor(environment, latents);
                    OnnxTensor timestepTensor  = createTimestepTensor(environment, unet, t);
                    OnnxTensor hiddenTensor    = OnnxTensor.createTensor(environment, textEmbeddings);
                    Map<String, OnnxTensor> unetInputs = new HashMap<>();
                    unetInputs.put(resolveInputName(unet, "sample", 0), sampleTensor);
                    unetInputs.put(resolveInputName(unet, "timestep", 1), timestepTensor);
                    unetInputs.put(resolveInputName(unet, "encoder_hidden_states", 2), hiddenTensor);

                    float[][][][] noise;
                    try (OrtSession.Result unetResult = unet.run(unetInputs)) {
                        noise = extractTensor4d(unetResult);
                    } finally {
                        sampleTensor.close();
                        timestepTensor.close();
                        hiddenTensor.close();
                    }

                    if (noise == null || noise.length == 0) {
                        return InferenceResult.fail("SD Turbo UNet produced invalid output.");
                    }

                    // Euler step: x_{t-1} = x_t - sigma * noise_pred
                    float sigma = turboSigma(t);
                    float sigmaPrev = (i + 1 < timesteps.length) ? turboSigma(timesteps[i + 1]) : 0f;
                    latents = eulerStep(latents, noise[0], sigma, sigmaPrev);

                    long elapsed = System.currentTimeMillis() - stepStart;
                    stepStart = System.currentTimeMillis();
                    int remaining = steps - (i + 1);
                    long eta = remaining * elapsed / 1000;
                    request.reportProgress("Denoising: " + (i + 1) + "/" + steps
                            + " steps (" + String.format("%.1f", elapsed / 1000.0) + "s/step, ETA: " + eta + "s)");

                    if (request.isCancelled()) {
                        return InferenceResult.fail("Cancelled by user.");
                    }
                }

                request.reportProgress("Decoding latents with VAE…");
                float[][][][] scaledLatents = scaleLatents(latents, 1f / 0.18215f);
                OnnxTensor latentTensor = OnnxTensor.createTensor(environment, scaledLatents);
                Map<String, OnnxTensor> vaeInputs = new HashMap<>();
                vaeInputs.put(resolveInputName(vaeDecoder, "latent", 0), latentTensor);

                float[][][][] decoded;
                try (OrtSession.Result vaeResult = vaeDecoder.run(vaeInputs)) {
                    decoded = extractTensor4d(vaeResult);
                } finally {
                    latentTensor.close();
                }

                if (decoded == null || decoded.length == 0) {
                    return InferenceResult.fail("VAE decoder output is empty.");
                }

                BufferedImage image = tensorToImage(decoded[0]);
                Path outputPath = writeOutputImage(image, "sd-turbo");
                return InferenceResult.ok(
                        "Generated image for prompt: \"" + request.prompt() + "\"",
                        "SD Turbo pipeline completed (" + steps + " steps) | EP=" + provider,
                        outputPath.toString(), "image");
            }
        } catch (Exception ex) {
            return InferenceResult.fail("SD Turbo pipeline failed: " + ex.getMessage());
        }
    }
}
