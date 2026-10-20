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
 * SDXL Turbo ONNX pipeline — dual text encoders (CLIP-L + OpenCLIP-bigG),
 * 1–4 step distilled pipeline, 512×512 native.
 */
class SdxlTurboOnnxPipeline extends OnnxPipelineBase {

    SdxlTurboOnnxPipeline(ModelStorage storage) {
        super(storage);
    }

    /**
     * Run the distilled SDXL Turbo pipeline (1–8 steps, no classifier-free guidance).
     * <p>
     * Uses dual text encoders (CLIP-L + OpenCLIP-bigG), concatenates their outputs,
     * and passes them together with pooled embeddings and time IDs into the UNet.
     * The denoising loop uses the same Euler single-step scheduler as SD Turbo.
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
            int latentW = width  / 8;
            int latentH = height / 8;
            int steps = Math.max(1, Math.min(request.batch() > 0 ? request.batch() : 4, 8));

            Path base = storage.root().resolve("text-image").resolve("sdxl-turbo");
            Path textEncoder1Path = base.resolve("text_encoder/model.onnx");
            Path textEncoder2Path = base.resolve("text_encoder_2/model.onnx");
            Path unetPath         = base.resolve("unet/model.onnx");
            Path vaeDecoderPath   = base.resolve("vae_decoder/model.onnx");
            Path vocab1 = base.resolve("tokenizer/vocab.json");
            Path merges1 = base.resolve("tokenizer/merges.txt");
            Path vocab2 = base.resolve("tokenizer_2/vocab.json");
            Path merges2 = base.resolve("tokenizer_2/merges.txt");

            for (Path p : List.of(textEncoder1Path, textEncoder2Path, unetPath, vaeDecoderPath,
                    vocab1, merges1, vocab2, merges2)) {
                if (!java.nio.file.Files.exists(p)) {
                    return InferenceResult.fail("SDXL Turbo bundle is incomplete (missing "
                            + p.getFileName() + "). Open Models → Model Manager and download SDXL Turbo.");
                }
            }

            ClipTokenizer tok1 = getOrCreateTokenizer(vocab1, merges1);
            ClipTokenizer tok2 = getOrCreateTokenizer(vocab2, merges2);
            long[] tokens1 = tok1.encode(request.prompt(), 77);
            long[] tokens2 = tok2.encode(request.prompt(), 77);

            request.reportProgress("Loading SDXL Turbo models (2 text encoders, UNet, VAE)…");
            OrtSession enc1 = getOrCreateSession(environment, textEncoder1Path, sessionOptions);
            OrtSession enc2 = getOrCreateSession(environment, textEncoder2Path, sessionOptions);
            OrtSession unet = getOrCreateSession(environment, unetPath, sessionOptions);
            OrtSession vae  = getOrCreateSession(environment, vaeDecoderPath, sessionOptions);
            {

                request.reportProgress("Encoding text (CLIP-L)…");
                float[][][] embed1 = runTextEncoder(environment, enc1, tokens1); // [1, 77, 768]

                request.reportProgress("Encoding text (OpenCLIP-bigG)…");
                // text_encoder_2 outputs both hidden_states [1, 77, 1280] and pooled [1, 1280]
                float[][][] embed2;
                float[][] pooledOutput;
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
                        embed2 = null;
                        pooledOutput = null;
                        for (Map.Entry<String, OnnxValue> entry : r) {
                            if (entry.getValue() instanceof OnnxTensor t) {
                                Object v = t.getValue();
                                if (v instanceof float[][][] arr3d && embed2 == null) {
                                    embed2 = arr3d;
                                } else if (v instanceof float[][] arr2d && pooledOutput == null) {
                                    pooledOutput = arr2d;
                                }
                            }
                        }
                        if (embed2 == null) {
                            return InferenceResult.fail("Text encoder 2 produced no hidden states.");
                        }
                        if (pooledOutput == null) {
                            // Fallback: use zeros for pooled output
                            pooledOutput = new float[1][1280];
                        }
                    } finally {
                        idsTensor.close();
                    }
                }

                // Concatenate embeddings: [1, 77, 768] + [1, 77, 1280] → [1, 77, 2048]
                int seqLen = embed1[0].length;
                int dim1 = embed1[0][0].length;
                int dim2 = embed2[0][0].length;
                float[][][] combined = new float[1][seqLen][dim1 + dim2];
                for (int s = 0; s < seqLen; s++) {
                    System.arraycopy(embed1[0][s], 0, combined[0][s], 0, dim1);
                    System.arraycopy(embed2[0][s], 0, combined[0][s], dim1, dim2);
                }

                // time_ids: [original_h, original_w, crop_y, crop_x, target_h, target_w]
                float[][] timeIds = {{height, width, 0, 0, height, width}};

                float[][][][] latents = randomLatents(request.seed(), latentH, latentW);
                int[] timesteps = turboTimesteps(steps);

                request.reportProgress("Denoising: 0/" + steps + " steps (SDXL Turbo) — EP: " + provider);
                long stepStart = System.currentTimeMillis();
                for (int i = 0; i < timesteps.length; i++) {
                    int t = timesteps[i];

                    OnnxTensor sampleT   = OnnxTensor.createTensor(environment, latents);
                    OnnxTensor tsT       = createTimestepTensor(environment, unet, t);
                    OnnxTensor hiddenT   = OnnxTensor.createTensor(environment, combined);
                    OnnxTensor embedsT   = OnnxTensor.createTensor(environment, pooledOutput);
                    OnnxTensor timeIdsT  = OnnxTensor.createTensor(environment, timeIds);

                    Map<String, OnnxTensor> unetInputs = new HashMap<>();
                    unetInputs.put(resolveInputName(unet, "sample", 0), sampleT);
                    unetInputs.put(resolveInputName(unet, "timestep", 1), tsT);
                    unetInputs.put(resolveInputName(unet, "encoder_hidden_states", 2), hiddenT);
                    // SDXL UNet has additional inputs: text_embeds and time_ids
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

                    if (noise == null || noise.length == 0) {
                        return InferenceResult.fail("SDXL Turbo UNet produced invalid output.");
                    }

                    float sigma = turboSigma(t);
                    float sigmaPrev = (i + 1 < timesteps.length) ? turboSigma(timesteps[i + 1]) : 0f;
                    latents = eulerStep(latents, noise[0], sigma, sigmaPrev);

                    long elapsed = System.currentTimeMillis() - stepStart;
                    stepStart = System.currentTimeMillis();
                    request.reportProgress("Denoising: " + (i + 1) + "/" + steps
                            + " steps (" + String.format("%.1f", elapsed / 1000.0) + "s/step)");

                    if (request.isCancelled()) {
                        return InferenceResult.fail("Cancelled by user.");
                    }
                }

                request.reportProgress("Decoding latents with VAE…");
                float[][][][] scaledLatents = scaleLatents(latents, 1f / 0.18215f);
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
                Path outputPath = writeOutputImage(image, "sdxl-turbo");
                return InferenceResult.ok(
                        "Generated image for prompt: \"" + request.prompt() + "\"",
                        "SDXL Turbo pipeline completed (" + steps + " steps) | EP=" + provider,
                        outputPath.toString(), "image");
            }
        } catch (Exception ex) {
            return InferenceResult.fail("SDXL Turbo pipeline failed: " + ex.getMessage());
        }
    }
}
