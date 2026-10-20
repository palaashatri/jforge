package atri.palaash.jforge.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import atri.palaash.jforge.storage.ModelStorage;
import atri.palaash.jforge.tokenize.ClipTokenizer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SDXL Base 1.0 ONNX pipeline — full SDXL with classifier-free guidance,
 * dual text encoders (CLIP-L + OpenCLIP-bigG), Euler Discrete scheduler,
 * 1024×1024 native resolution.
 */
class SdxlBaseOnnxPipeline extends OnnxPipelineBase {

    SdxlBaseOnnxPipeline(ModelStorage storage) {
        super(storage);
    }

    /**
     * Run the full SDXL Base 1.0 pipeline with classifier-free guidance and Euler Discrete scheduler.
     * <p>
     * Dual text encoders, batched (neg + pos) embeddings, time IDs, and a full
     * denoising schedule (typically 30 steps) produce 1024×1024 images.
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
            int width  = Math.max(512, (request.width()  / 8) * 8);
            int height = Math.max(512, (request.height() / 8) * 8);
            int latentW = width  / 8;
            int latentH = height / 8;
            int steps = Math.max(5, Math.min(request.batch() > 0 ? request.batch() : 30, 50));
            float guidanceScale = request.promptWeight() > 0 ? (float) request.promptWeight() : 7.5f;

            Path base = storage.root().resolve("text-image").resolve("sdxl-base");
            Path textEncoder1Path = base.resolve("text_encoder/model.onnx");
            Path textEncoder2Path = base.resolve("text_encoder_2/model.onnx");
            Path unetPath         = base.resolve("unet/model.onnx");
            Path vaeDecoderPath   = base.resolve("vae_decoder/model.onnx");
            Path schedulerConfig  = base.resolve("scheduler/scheduler_config.json");
            Path vocab1 = base.resolve("tokenizer/vocab.json");
            Path merges1 = base.resolve("tokenizer/merges.txt");
            Path vocab2 = base.resolve("tokenizer_2/vocab.json");
            Path merges2 = base.resolve("tokenizer_2/merges.txt");

            for (Path p : List.of(textEncoder1Path, textEncoder2Path, unetPath, vaeDecoderPath,
                    vocab1, merges1, vocab2, merges2)) {
                if (!java.nio.file.Files.exists(p)) {
                    return InferenceResult.fail("SDXL Base bundle is incomplete (missing "
                            + p.getFileName() + "). Open Models → Model Manager and download SDXL Base 1.0.");
                }
            }

            ClipTokenizer tok1 = getOrCreateTokenizer(vocab1, merges1);
            ClipTokenizer tok2 = getOrCreateTokenizer(vocab2, merges2);
            long[] tokens1 = tok1.encode(request.prompt(), 77);
            long[] tokens2 = tok2.encode(request.prompt(), 77);
            long[] negTokens1 = tok1.encode(request.negativePrompt() == null ? "" : request.negativePrompt(), 77);
            long[] negTokens2 = tok2.encode(request.negativePrompt() == null ? "" : request.negativePrompt(), 77);

            request.reportProgress("Loading SDXL Base models (2 text encoders, UNet, VAE)…");
            OrtSession enc1 = getOrCreateSession(environment, textEncoder1Path, sessionOptions);
            OrtSession enc2 = getOrCreateSession(environment, textEncoder2Path, sessionOptions);
            OrtSession unet = getOrCreateSession(environment, unetPath, sessionOptions);
            OrtSession vae  = getOrCreateSession(environment, vaeDecoderPath, sessionOptions);
            {
                // ── Encode positive prompt with both encoders ──
                request.reportProgress("Encoding positive prompt (CLIP-L)…");
                float[][][] embed1 = runTextEncoder(environment, enc1, tokens1);

                request.reportProgress("Encoding positive prompt (OpenCLIP-bigG)…");
                float[][][] embed2;
                float[][] pooledPos;
                {
                    String inName = resolveInputName(enc2, "input_ids", 0);
                    TensorInfo tInfo = (TensorInfo) enc2.getInputInfo().get(inName).getInfo();
                    boolean wantsInt32 = tInfo.type.toString().contains("INT32");
                    OnnxTensor idsTensor;
                    if (wantsInt32) {
                        int[] ids32 = new int[tokens2.length];
                        for (int i = 0; i < tokens2.length; i++) { ids32[i] = (int) tokens2[i]; }
                        idsTensor = OnnxTensor.createTensor(environment, new int[][]{ids32});
                    } else {
                        idsTensor = OnnxTensor.createTensor(environment, new long[][]{tokens2});
                    }
                    Map<String, OnnxTensor> inputs = new HashMap<>();
                    inputs.put(inName, idsTensor);
                    try (OrtSession.Result r = enc2.run(inputs)) {
                        embed2 = null; pooledPos = null;
                        for (Map.Entry<String, OnnxValue> entry : r) {
                            if (entry.getValue() instanceof OnnxTensor t) {
                                Object v = t.getValue();
                                if (v instanceof float[][][] a3 && embed2 == null) { embed2 = a3; }
                                else if (v instanceof float[][] a2 && pooledPos == null) { pooledPos = a2; }
                            }
                        }
                        if (embed2 == null) { return InferenceResult.fail("Text encoder 2 produced no hidden states."); }
                        if (pooledPos == null) { pooledPos = new float[1][1280]; }
                    } finally { idsTensor.close(); }
                }

                // ── Encode negative prompt with both encoders ──
                request.reportProgress("Encoding negative prompt…");
                float[][][] negEmbed1 = runTextEncoder(environment, enc1, negTokens1);
                float[][][] negEmbed2;
                float[][] pooledNeg;
                {
                    String inName = resolveInputName(enc2, "input_ids", 0);
                    TensorInfo tInfo = (TensorInfo) enc2.getInputInfo().get(inName).getInfo();
                    boolean wantsInt32 = tInfo.type.toString().contains("INT32");
                    OnnxTensor idsTensor;
                    if (wantsInt32) {
                        int[] ids32 = new int[negTokens2.length];
                        for (int i = 0; i < negTokens2.length; i++) { ids32[i] = (int) negTokens2[i]; }
                        idsTensor = OnnxTensor.createTensor(environment, new int[][]{ids32});
                    } else {
                        idsTensor = OnnxTensor.createTensor(environment, new long[][]{negTokens2});
                    }
                    Map<String, OnnxTensor> inputs = new HashMap<>();
                    inputs.put(inName, idsTensor);
                    try (OrtSession.Result r = enc2.run(inputs)) {
                        negEmbed2 = null; pooledNeg = null;
                        for (Map.Entry<String, OnnxValue> entry : r) {
                            if (entry.getValue() instanceof OnnxTensor t) {
                                Object v = t.getValue();
                                if (v instanceof float[][][] a3 && negEmbed2 == null) { negEmbed2 = a3; }
                                else if (v instanceof float[][] a2 && pooledNeg == null) { pooledNeg = a2; }
                            }
                        }
                        if (negEmbed2 == null) { return InferenceResult.fail("Text encoder 2 negative produced no hidden states."); }
                        if (pooledNeg == null) { pooledNeg = new float[1][1280]; }
                    } finally { idsTensor.close(); }
                }

                // Concatenate embeddings: [1, 77, 768] + [1, 77, 1280] → [1, 77, 2048]
                int seqLen = embed1[0].length;
                int dim1 = embed1[0][0].length;
                int dim2 = embed2[0][0].length;
                float[][][] combinedPos = new float[1][seqLen][dim1 + dim2];
                float[][][] combinedNeg = new float[1][seqLen][dim1 + dim2];
                for (int s = 0; s < seqLen; s++) {
                    System.arraycopy(embed1[0][s], 0, combinedPos[0][s], 0, dim1);
                    System.arraycopy(embed2[0][s], 0, combinedPos[0][s], dim1, dim2);
                    System.arraycopy(negEmbed1[0][s], 0, combinedNeg[0][s], 0, dim1);
                    System.arraycopy(negEmbed2[0][s], 0, combinedNeg[0][s], dim1, dim2);
                }
                // Batch embeddings: [2, 77, 2048] — negative first, positive second
                float[][][] batchedHidden = new float[2][seqLen][dim1 + dim2];
                batchedHidden[0] = combinedNeg[0];
                batchedHidden[1] = combinedPos[0];

                // Batch pooled: [2, 1280]
                int poolDim = pooledPos[0].length;
                float[][] batchedPooled = new float[2][poolDim];
                System.arraycopy(pooledNeg[0], 0, batchedPooled[0], 0, poolDim);
                System.arraycopy(pooledPos[0], 0, batchedPooled[1], 0, poolDim);

                // time_ids: [original_h, original_w, crop_y, crop_x, target_h, target_w] — batched [2, 6]
                float[][] timeIds = {
                    {height, width, 0, 0, height, width},
                    {height, width, 0, 0, height, width}
                };

                // ── Noise schedule from scheduler config ──
                float[] alphaCumprod;
                if (java.nio.file.Files.exists(schedulerConfig)) {
                    alphaCumprod = loadAlphaCumprod(schedulerConfig);
                } else {
                    // Compute default SDXL schedule inline
                    alphaCumprod = computeDefaultAlphaCumprod(1000, 0.00085, 0.012);
                }
                int[] timesteps = createTimesteps(steps, alphaCumprod.length);

                // Convert alphas to sigmas for Euler Discrete
                float[] sigmas = new float[timesteps.length + 1];
                for (int i = 0; i < timesteps.length; i++) {
                    float acp = alphaCumprod[Math.min(timesteps[i], alphaCumprod.length - 1)];
                    sigmas[i] = (float) Math.sqrt((1.0 - acp) / acp);
                }
                sigmas[timesteps.length] = 0f; // final sigma

                float[][][][] latents = randomLatents(request.seed(), latentH, latentW);
                // Scale initial noise by first sigma
                latents = scaleLatents(latents, sigmas[0]);

                request.reportProgress("Denoising: 0/" + steps + " steps (SDXL Base) — EP: " + provider);
                long stepStart = System.currentTimeMillis();
                long firstStepDuration = 0;
                for (int i = 0; i < timesteps.length; i++) {
                    int t = timesteps[i];
                    float sigma = sigmas[i];
                    float sigmaPrev = sigmas[i + 1];

                    // Scale model input: sample / sqrt(sigma^2 + 1)
                    float scaleFactor = (float) (1.0 / Math.sqrt(sigma * sigma + 1.0));
                    float[][][][] scaledInput = scaleLatents(latents, scaleFactor);

                    // Duplicate for CFG batch [2, 4, H, W]
                    float[][][][] batchInput = duplicateBatch(scaledInput);

                    OnnxTensor sampleT   = OnnxTensor.createTensor(environment, batchInput);
                    OnnxTensor tsT       = createTimestepTensor(environment, unet, t);
                    OnnxTensor hiddenT   = OnnxTensor.createTensor(environment, batchedHidden);
                    OnnxTensor embedsT   = OnnxTensor.createTensor(environment, batchedPooled);
                    OnnxTensor timeIdsT  = OnnxTensor.createTensor(environment, timeIds);

                    Map<String, OnnxTensor> unetInputs = new HashMap<>();
                    unetInputs.put(resolveInputName(unet, "sample", 0), sampleT);
                    unetInputs.put(resolveInputName(unet, "timestep", 1), tsT);
                    unetInputs.put(resolveInputName(unet, "encoder_hidden_states", 2), hiddenT);
                    for (String name : unet.getInputNames()) {
                        if (name.contains("text_embeds") || name.contains("added_cond_kwargs.text_embeds")) {
                            unetInputs.put(name, embedsT);
                        } else if (name.contains("time_ids") || name.contains("added_cond_kwargs.time_ids")) {
                            unetInputs.put(name, timeIdsT);
                        }
                    }

                    float[][][][] noise;
                    try (OrtSession.Result unetResult = unet.run(unetInputs)) {
                        noise = extractTensor4d(unetResult);
                    } finally {
                        sampleT.close(); tsT.close(); hiddenT.close(); embedsT.close(); timeIdsT.close();
                    }

                    if (noise == null || noise.length < 2) {
                        return InferenceResult.fail("SDXL Base UNet produced invalid output.");
                    }

                    // Classifier-free guidance
                    float[][][] guidedNoise = guidance(noise[0], noise[1], guidanceScale);

                    // Euler step
                    latents = eulerStep(latents, guidedNoise, sigma, sigmaPrev);

                    long elapsed = System.currentTimeMillis() - stepStart;
                    stepStart = System.currentTimeMillis();
                    if (i == 0) { firstStepDuration = elapsed; }
                    int remaining = steps - (i + 1);
                    long avgMs = (i == 0) ? firstStepDuration : elapsed;
                    long etaSec = (remaining * avgMs) / 1000;
                    String eta = etaSec > 60
                            ? String.format("%dm %02ds", etaSec / 60, etaSec % 60)
                            : etaSec + "s";
                    request.reportProgress("Denoising: " + (i + 1) + "/" + steps
                            + " steps (" + String.format("%.1f", elapsed / 1000.0) + "s/step, ETA: " + eta + ")");

                    if (request.isCancelled()) {
                        return InferenceResult.fail("Cancelled by user.");
                    }
                }

                // SDXL VAE uses 0.13025 scaling factor
                request.reportProgress("Decoding latents with VAE…");
                float[][][][] scaledLatents = scaleLatents(latents, 1f / 0.13025f);
                OnnxTensor latTensor = OnnxTensor.createTensor(environment, scaledLatents);
                Map<String, OnnxTensor> vaeIn = new HashMap<>();
                vaeIn.put(resolveInputName(vae, "latent", 0), latTensor);

                float[][][][] decoded;
                try (OrtSession.Result vaeResult = vae.run(vaeIn)) {
                    decoded = extractTensor4d(vaeResult);
                } finally {
                    latTensor.close();
                }

                if (decoded == null || decoded.length == 0) {
                    return InferenceResult.fail("VAE decoder output is empty.");
                }

                BufferedImage image = tensorToImage(decoded[0]);
                Path outputPath = writeOutputImage(image, "sdxl-base");
                return InferenceResult.ok(
                        "Generated image for prompt: \"" + request.prompt() + "\"",
                        "SDXL Base 1.0 pipeline completed (" + steps + " steps, CFG=" + guidanceScale + ") | EP=" + provider,
                        outputPath.toString(), "image");
            }
        } catch (Exception ex) {
            return InferenceResult.fail("SDXL Base pipeline failed: " + ex.getMessage());
        }
    }
}
