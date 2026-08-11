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
 * Stable Diffusion v1.5 ONNX pipeline: CLIP text encoding, DDIM denoising
 * with classifier-free guidance, and VAE decoding.
 */
class Sd15OnnxPipeline extends OnnxPipelineBase {

    Sd15OnnxPipeline(ModelStorage storage) {
        super(storage);
    }

    /**
     * Run the full Stable Diffusion v1.5 pipeline: text encoding, DDIM denoising (with
     * classifier-free guidance), and VAE decoding.
     * <p>
     * Steps: tokenize prompt → CLIP text encoder → create random latents → DDIM loop
     * (UNet predicts noise → guidance → step) → VAE decoder → save image.
     * </p>
     *
     * @param environment   shared ONNX Runtime environment
     * @param sessionOptions session configuration (optimisation levels, thread counts, EP)
     * @param request       user's inference request (prompt, size, steps, etc.)
     * @param provider      display name of the execution provider in use
     * @return the result containing the generated image path, or a failure message
     */
    InferenceResult run(OrtEnvironment environment,
                        OrtSession.SessionOptions sessionOptions,
                        InferenceRequest request,
                        String provider) {
        try {
            int width = Math.max(256, (request.width() / 8) * 8);
            int height = Math.max(256, (request.height() / 8) * 8);
            int latentWidth = width / 8;
            int latentHeight = height / 8;
            int steps = Math.max(5, Math.min(request.batch() > 0 ? request.batch() : 15, 50));
            float guidanceScale = 7.5f;

            Path base = storage.root().resolve("text-image").resolve("stable-diffusion-v15");
            Path textEncoderPath = base.resolve("text_encoder/model.onnx");
            Path unetPath = base.resolve("unet/model.onnx");
            Path vaeDecoderPath = base.resolve("vae_decoder/model.onnx");
            Path vocabPath = base.resolve("tokenizer/vocab.json");
            Path mergesPath = base.resolve("tokenizer/merges.txt");
            Path schedulerPath = base.resolve("scheduler/scheduler_config.json");
            List<Path> required = List.of(textEncoderPath, unetPath, vaeDecoderPath, vocabPath, mergesPath, schedulerPath);
            for (Path path : required) {
                if (!java.nio.file.Files.exists(path)) {
                    return InferenceResult.fail("Stable Diffusion bundle is incomplete. Open menu bar → Models → Open Model Manager and redownload Stable Diffusion v1.5 ONNX.");
                }
            }

            ClipTokenizer tokenizer = getOrCreateTokenizer(vocabPath, mergesPath);
            long[] promptTokens = tokenizer.encode(request.prompt(), 77);
            long[] negativeTokens = tokenizer.encode(request.negativePrompt() == null ? "" : request.negativePrompt(), 77);

            request.reportProgress("Loading models (text encoder, UNet, VAE decoder)…");
            OrtSession textEncoder = getOrCreateSession(environment, textEncoderPath, sessionOptions);
            OrtSession unet = getOrCreateSession(environment, unetPath, sessionOptions);
            OrtSession vaeDecoder = getOrCreateSession(environment, vaeDecoderPath, sessionOptions);
            {

                request.reportProgress("Encoding text prompt…");
                float[][][] textEmbeddings = runTextEncoder(environment, textEncoder, promptTokens);
                float[][][] negativeEmbeddings = runTextEncoder(environment, textEncoder, negativeTokens);
                float[][][] encoderHidden = new float[2][textEmbeddings[0].length][textEmbeddings[0][0].length];
                encoderHidden[0] = negativeEmbeddings[0];
                encoderHidden[1] = textEmbeddings[0];

                float[][][][] latents = randomLatents(request.seed(), latentHeight, latentWidth);
                float[] alphaCumprod = loadAlphaCumprod(schedulerPath);
                int[] timesteps = createTimesteps(steps, alphaCumprod.length);

                request.reportProgress("Denoising: 0/" + steps + " steps — EP: " + provider);
                long stepStartTime = System.currentTimeMillis();
                long firstStepDuration = 0;
                for (int stepIndex = 0; stepIndex < timesteps.length; stepIndex++) {
                    int timestep = timesteps[stepIndex];
                    int prevTimestep = stepIndex == timesteps.length - 1 ? 0 : timesteps[stepIndex + 1];

                    float[][][][] latentModelInput = duplicateBatch(latents);
                    Map<String, OnnxTensor> unetInputs = new HashMap<>();
                    OnnxTensor sampleTensor = OnnxTensor.createTensor(environment, latentModelInput);
                    OnnxTensor timestepTensor = createTimestepTensor(environment, unet, timestep);
                    OnnxTensor hiddenTensor = OnnxTensor.createTensor(environment, encoderHidden);
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

                    if (noise == null || noise.length < 2) {
                        return InferenceResult.fail("UNet output is invalid for Stable Diffusion generation.");
                    }

                    float[][][] guidedNoise = guidance(noise[0], noise[1], guidanceScale);
                    latents = ddimStep(latents, guidedNoise, alphaCumprod[timestep], alphaCumprod[prevTimestep]);

                    long elapsed = System.currentTimeMillis() - stepStartTime;
                    stepStartTime = System.currentTimeMillis();
                    if (stepIndex == 0) { firstStepDuration = elapsed; }
                    int remaining = steps - (stepIndex + 1);
                    long avgMs = (stepIndex == 0) ? firstStepDuration : elapsed;
                    long etaSec = (remaining * avgMs) / 1000;
                    String eta = etaSec > 60
                            ? String.format("%dm %02ds", etaSec / 60, etaSec % 60)
                            : etaSec + "s";
                    request.reportProgress("Denoising: " + (stepIndex + 1) + "/" + steps
                            + " steps (" + String.format("%.1f", elapsed / 1000.0) + "s/step, ETA: " + eta + ")");

                    if (request.isCancelled()) {
                        return InferenceResult.fail("Cancelled by user.");
                    }

                    if (provider.contains("CoreML") && stepIndex % 5 == 4) {
                        System.gc();
                        System.runFinalization();
                    }
                }

                request.reportProgress("Decoding latents with VAE…");
                float[][][][] scaledLatents = scaleLatents(latents, 1f / 0.18215f);
                Map<String, OnnxTensor> vaeInputs = new HashMap<>();
                OnnxTensor latentTensor = OnnxTensor.createTensor(environment, scaledLatents);
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
                Path outputPath = writeOutputImage(image, "sd-v15");
                String details = "Stable Diffusion v1.5 pipeline completed | EP=" + provider;
                String output = "Generated image for prompt: \"" + request.prompt() + "\"";
                return InferenceResult.ok(output, details, outputPath.toString(), "image");
            }
        } catch (Exception ex) {
            return InferenceResult.fail("Stable Diffusion v1.5 pipeline failed: " + ex.getMessage());
        }
    }
}
