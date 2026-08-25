package atri.palaash.jforge.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import atri.palaash.jforge.engine.scheduler.FlowMatchEulerScheduler;
import atri.palaash.jforge.storage.ModelStorage;
import atri.palaash.jforge.tokenize.ClipTokenizer;
import atri.palaash.jforge.tokenize.T5Tokenizer;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Stable Diffusion 3.x ONNX pipeline — MMDiT transformer, Flow Matching Euler,
 * dual CLIP encoders (L + G) with optional T5, 16-channel latents, 1024×1024.
 */
class Sd3OnnxPipeline extends OnnxPipelineBase {

    Sd3OnnxPipeline(ModelStorage storage) {
        super(storage);
    }

    /**
     * Run the Stable Diffusion 3.x pipeline (MMDiT transformer, Flow Matching Euler scheduler).
     * <p>
     * SD 3.x uses three text encoders (CLIP-L, CLIP-G, optional T5-XXL), 16-channel latents,
     * and a flow-matching denoising objective. Each encoder is loaded, run, and evicted
     * sequentially to keep peak memory under control (~6 GB out of ~12 GB total model size).
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
            int steps = Math.max(5, Math.min(request.batch() > 0 ? request.batch() : 28, 50));
            float guidanceScale = request.promptWeight() > 0 ? (float) request.promptWeight() : 7.0f;
            float shiftFactor = 3.0f; // SD 3.5 medium schedule shift

            // Resolve model directory from the relativePath
            Path modelPath = storage.modelPath(request.model());
            Path base = modelPath.getParent().getParent(); // go up from transformer/model.onnx

            Path transformerPath = base.resolve("transformer/model.onnx");
            Path textEncoder1Path = base.resolve("text_encoder/model.onnx");
            Path textEncoder2Path = base.resolve("text_encoder_2/model.onnx");
            Path vaeDecoderPath   = base.resolve("vae_decoder/model.onnx");
            Path vocab1 = base.resolve("tokenizer/vocab.json");
            Path merges1 = base.resolve("tokenizer/merges.txt");
            Path vocab2 = base.resolve("tokenizer_2/vocab.json");
            Path merges2 = base.resolve("tokenizer_2/merges.txt");

            for (Path p : List.of(transformerPath, textEncoder1Path, textEncoder2Path,
                    vaeDecoderPath, vocab1, merges1, vocab2, merges2)) {
                if (!java.nio.file.Files.exists(p)) {
                    return InferenceResult.fail("SD 3.x bundle is incomplete (missing "
                            + base.relativize(p) + "). Convert the full model first.");
                }
            }

            // Optional T5 text encoder
            Path textEncoder3Path = base.resolve("text_encoder_3/model.onnx");
            Path tokenizer3Json   = base.resolve("tokenizer_3/tokenizer.json");
            boolean hasT5 = java.nio.file.Files.exists(textEncoder3Path)
                    && java.nio.file.Files.exists(tokenizer3Json);

            ClipTokenizer tok1 = getOrCreateTokenizer(vocab1, merges1);
            ClipTokenizer tok2 = getOrCreateTokenizer(vocab2, merges2);
            T5Tokenizer tok3 = hasT5 ? getOrCreateT5Tokenizer(tokenizer3Json) : null;
            long[] tokens1 = tok1.encode(request.prompt(), 77);
            long[] tokens2 = tok2.encode(request.prompt(), 77);
            long[] negTokens1 = tok1.encode(request.negativePrompt() == null ? "" : request.negativePrompt(), 77);
            long[] negTokens2 = tok2.encode(request.negativePrompt() == null ? "" : request.negativePrompt(), 77);
            long[] t5Tokens = hasT5 ? tok3.encode(request.prompt(), 256) : null;
            long[] t5NegTokens = hasT5 ? tok3.encode(
                    request.negativePrompt() == null ? "" : request.negativePrompt(), 256) : null;

            // ── Load text encoders sequentially, free each after use ──
            // SD 3.5 models are large (~12 GB total); loading all at once
            // can OOM a 16 GB machine. We load → encode → evict each encoder
            // before moving to the next, keeping peak memory at ~6 GB.

            request.reportProgress("Encoding positive prompt (CLIP-L)…");
            OrtSession enc1 = getOrCreateSession(environment, textEncoder1Path, sessionOptions);
            float[][][] embed1 = runTextEncoder(environment, enc1, tokens1);
            float[][] embed1Pooled = runTextEncoderPooled(environment, enc1, tokens1);
            float[][][] negEmbed1 = runTextEncoder(environment, enc1, negTokens1);
            float[][] negEmbed1Pooled = runTextEncoderPooled(environment, enc1, negTokens1);
            evictSession(textEncoder1Path);  // ~250 MB freed

            request.reportProgress("Encoding positive prompt (CLIP-G)…");
            OrtSession enc2 = getOrCreateSession(environment, textEncoder2Path, sessionOptions);
            float[][][] embed2;
            float[][] pooledPos;
            {
                String inName = resolveInputName(enc2, "input_ids", 0);
                TensorInfo tInfo = (TensorInfo) enc2.getInputInfo().get(inName).getInfo();
                boolean wantsInt32 = tInfo.type.toString().contains("INT32");
                OnnxTensor idsTensor;
                if (wantsInt32) {
                    int[] ids32 = new int[tokens2.length];
                    for (int i = 0; i < tokens2.length; i++) ids32[i] = (int) tokens2[i];
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
                            if (v instanceof float[][][] a3 && embed2 == null) embed2 = a3;
                            else if (v instanceof float[][] a2 && pooledPos == null) pooledPos = a2;
                        }
                    }
                    if (embed2 == null) return InferenceResult.fail("CLIP-G produced no hidden states.");
                    if (pooledPos == null) pooledPos = new float[1][1280];
                } finally { idsTensor.close(); }
            }

            // ── Encode negative prompt (CLIP-G) ──
            request.reportProgress("Encoding negative prompt (CLIP-G)…");
            float[][][] negEmbed2;
            float[][] pooledNeg;
            {
                String inName = resolveInputName(enc2, "input_ids", 0);
                TensorInfo tInfo = (TensorInfo) enc2.getInputInfo().get(inName).getInfo();
                boolean wantsInt32 = tInfo.type.toString().contains("INT32");
                OnnxTensor idsTensor;
                if (wantsInt32) {
                    int[] ids32 = new int[negTokens2.length];
                    for (int i = 0; i < negTokens2.length; i++) ids32[i] = (int) negTokens2[i];
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
                            if (v instanceof float[][][] a3 && negEmbed2 == null) negEmbed2 = a3;
                            else if (v instanceof float[][] a2 && pooledNeg == null) pooledNeg = a2;
                        }
                    }
                    if (negEmbed2 == null) return InferenceResult.fail("CLIP-G negative produced no hidden states.");
                    if (pooledNeg == null) pooledNeg = new float[1][1280];
                } finally { idsTensor.close(); }
            }
            evictSession(textEncoder2Path);  // ~1.4 GB freed

            // ── Combine CLIP embeddings ──
            // CLIP-L: [1, 77, dim1], CLIP-G: [1, 77, dim2]
            // Concatenate along last dim → [1, 77, dim1+dim2]
            int seqLen = embed1[0].length;
            int dim1 = embed1[0][0].length;  // 768
            int dim2 = embed2[0][0].length;  // 1280
            int clipDim = dim1 + dim2;       // 2048
            int t5Dim = 4096;                // SD3 transformer expects 4096-wide embeddings
            int t5SeqLen = 256;              // T5 max sequence length in SD3

            // Pad CLIP embeddings to t5Dim (4096) → [1, 77, 4096]
            float[][][] clipPosEmbed = new float[1][seqLen][t5Dim];
            float[][][] clipNegEmbed = new float[1][seqLen][t5Dim];
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(embed1[0][s], 0, clipPosEmbed[0][s], 0, dim1);
                System.arraycopy(embed2[0][s], 0, clipPosEmbed[0][s], dim1, dim2);
                // Rest is zeros (padding to 4096)
                System.arraycopy(negEmbed1[0][s], 0, clipNegEmbed[0][s], 0, dim1);
                System.arraycopy(negEmbed2[0][s], 0, clipNegEmbed[0][s], dim1, dim2);
            }

            // Release raw CLIP embeddings — they've been copied into clipPos/NegEmbed
            embed1 = null; embed2 = null; negEmbed1 = null; negEmbed2 = null;

            // T5 embeddings: real T5-XXL output or zeros fallback
            float[][][] t5PosEmbed;
            float[][][] t5NegEmbed;
            if (hasT5 && t5Tokens != null) {
                request.reportProgress("Encoding positive prompt (T5-XXL)…");
                OrtSession enc3 = getOrCreateSession(environment, textEncoder3Path, sessionOptions);
                t5PosEmbed = runT5Encoder(environment, enc3, t5Tokens, t5SeqLen, t5Dim);
                request.reportProgress("Encoding negative prompt (T5-XXL)…");
                t5NegEmbed = runT5Encoder(environment, enc3, t5NegTokens, t5SeqLen, t5Dim);
                evictSession(textEncoder3Path);  // ~4.5 GB freed
            } else {
                t5PosEmbed = new float[1][t5SeqLen][t5Dim]; // zeros fallback
                t5NegEmbed = new float[1][t5SeqLen][t5Dim];
            }

            // Concatenate along sequence: [1, 77+256, 4096] = [1, 333, 4096]
            int totalSeqLen = seqLen + t5SeqLen;
            float[][][] encoderHiddenPos = new float[1][totalSeqLen][t5Dim];
            float[][][] encoderHiddenNeg = new float[1][totalSeqLen][t5Dim];
            for (int s = 0; s < seqLen; s++) {
                System.arraycopy(clipPosEmbed[0][s], 0, encoderHiddenPos[0][s], 0, t5Dim);
                System.arraycopy(clipNegEmbed[0][s], 0, encoderHiddenNeg[0][s], 0, t5Dim);
            }
            for (int s = 0; s < t5SeqLen; s++) {
                System.arraycopy(t5PosEmbed[0][s], 0, encoderHiddenPos[0][seqLen + s], 0, t5Dim);
                System.arraycopy(t5NegEmbed[0][s], 0, encoderHiddenNeg[0][seqLen + s], 0, t5Dim);
            }

            // Batch hidden states [2, 333, 4096] — negative first, positive second (for CFG)
            float[][][] batchedHidden = new float[2][totalSeqLen][t5Dim];
            batchedHidden[0] = encoderHiddenNeg[0];
            batchedHidden[1] = encoderHiddenPos[0];

            // Release per-encoder embedding arrays now that they're combined
            clipPosEmbed = null; clipNegEmbed = null;
            t5PosEmbed = null; t5NegEmbed = null;
            encoderHiddenPos = null; encoderHiddenNeg = null;

            // Pooled projections: concat CLIP-L pooled + CLIP-G pooled → [1, 2048]
            // embed1Pooled / negEmbed1Pooled already computed during enc1 phase above

            int pooledDim = (embed1Pooled != null ? embed1Pooled[0].length : dim1) + pooledPos[0].length;
            float[][] batchedPooled = new float[2][pooledDim];
            // Negative
            if (negEmbed1Pooled != null) System.arraycopy(negEmbed1Pooled[0], 0, batchedPooled[0], 0, negEmbed1Pooled[0].length);
            System.arraycopy(pooledNeg[0], 0, batchedPooled[0], pooledDim - pooledNeg[0].length, pooledNeg[0].length);
            // Positive
            if (embed1Pooled != null) System.arraycopy(embed1Pooled[0], 0, batchedPooled[1], 0, embed1Pooled[0].length);
            System.arraycopy(pooledPos[0], 0, batchedPooled[1], pooledDim - pooledPos[0].length, pooledPos[0].length);

            // ── Create initial noise (16-channel latents for SD3) ──
            Random random = new Random(request.seed());
            float[][][][] latents = new float[1][16][latentH][latentW];
            for (int c = 0; c < 16; c++)
                for (int y = 0; y < latentH; y++)
                    for (int x = 0; x < latentW; x++)
                        latents[0][c][y][x] = (float) random.nextGaussian();

            // ── Flow Matching Euler schedule ──
            // SD 3.5 uses a shifted sigma schedule: sigma = shift * t / (1 + (shift-1)*t)
            FlowMatchEulerScheduler flowScheduler = new FlowMatchEulerScheduler(shiftFactor);
            float[] sigmas = new float[steps + 1];
            for (int i = 0; i <= steps; i++) {
                float t = 1.0f - (float) i / steps; // goes from 1.0 to 0.0
                sigmas[i] = flowScheduler.shifted(t);
            }

            // Release pooled arrays after batchedPooled is built
            embed1Pooled = null; negEmbed1Pooled = null;
            pooledPos = null; pooledNeg = null;

            // ── Load transformer on-demand (~6 GB) ──
            request.reportProgress("Loading transformer…");
            OrtSession transformer = getOrCreateSession(environment, transformerPath, sessionOptions);

            request.reportProgress("Denoising: 0/" + steps + " steps (SD 3.x Flow Matching) — EP: " + provider);
            long stepStart = System.currentTimeMillis();
            long firstStepDuration = 0;

            for (int i = 0; i < steps; i++) {
                float sigma = sigmas[i];
                float sigmaNext = sigmas[i + 1];

                // Scale timestep to 1000-scale for the model
                float timestepVal = sigma * 1000.0f;

                // Duplicate latents for CFG batch [2, 16, H, W]
                float[][][][] batchInput = new float[2][16][latentH][latentW];
                for (int c = 0; c < 16; c++)
                    for (int y = 0; y < latentH; y++)
                        for (int x = 0; x < latentW; x++) {
                            batchInput[0][c][y][x] = latents[0][c][y][x];
                            batchInput[1][c][y][x] = latents[0][c][y][x];
                        }

                OnnxTensor sampleT  = OnnxTensor.createTensor(environment, batchInput);
                OnnxTensor hiddenT  = OnnxTensor.createTensor(environment, batchedHidden);
                OnnxTensor pooledT  = OnnxTensor.createTensor(environment, batchedPooled);

                // Create timestep tensor — SD3 transformer may expect float or int
                OnnxTensor tsT;
                {
                    String tsName = resolveInputName(transformer, "timestep", 1);
                    TensorInfo tsInfo = (TensorInfo) transformer.getInputInfo().get(tsName).getInfo();
                    if (tsInfo.type.toString().contains("INT64")) {
                        tsT = OnnxTensor.createTensor(environment, new long[]{(long) timestepVal, (long) timestepVal});
                    } else if (tsInfo.type.toString().contains("INT32")) {
                        tsT = OnnxTensor.createTensor(environment, new int[]{(int) timestepVal, (int) timestepVal});
                    } else {
                        tsT = OnnxTensor.createTensor(environment, new float[]{timestepVal, timestepVal});
                    }
                }

                Map<String, OnnxTensor> transInputs = new HashMap<>();
                // Map inputs by sniffing the model's input names
                for (String name : transformer.getInputNames()) {
                    String lower = name.toLowerCase();
                    if (lower.contains("hidden_states") || lower.contains("sample")) {
                        transInputs.put(name, sampleT);
                    } else if (lower.contains("timestep")) {
                        transInputs.put(name, tsT);
                    } else if (lower.contains("encoder_hidden") || lower.contains("prompt_embeds")) {
                        transInputs.put(name, hiddenT);
                    } else if (lower.contains("pooled") || lower.contains("text_embeds")) {
                        transInputs.put(name, pooledT);
                    }
                }

                // Fallback: assign by positional index if mapping is empty
                if (transInputs.isEmpty()) {
                    var inputNames = new java.util.ArrayList<>(transformer.getInputNames());
                    if (inputNames.size() >= 4) {
                        transInputs.put(inputNames.get(0), sampleT);
                        transInputs.put(inputNames.get(1), tsT);
                        transInputs.put(inputNames.get(2), hiddenT);
                        transInputs.put(inputNames.get(3), pooledT);
                    }
                }

                float[][][][] noise;
                try (OrtSession.Result transResult = transformer.run(transInputs)) {
                    noise = extractTensor4d(transResult);
                } finally {
                    sampleT.close(); tsT.close(); hiddenT.close(); pooledT.close();
                }

                if (noise == null || noise.length < 2) {
                    return InferenceResult.fail("SD 3.x transformer produced invalid output.");
                }

                // Classifier-free guidance
                float[][][] guidedNoise = guidance(noise[0], noise[1], guidanceScale);

                // Flow matching Euler step: latent = latent + (sigma_next - sigma) * velocity
                float dt = sigmaNext - sigma;
                for (int c = 0; c < 16; c++)
                    for (int y = 0; y < latentH; y++)
                        for (int x = 0; x < latentW; x++)
                            latents[0][c][y][x] += dt * guidedNoise[c][y][x];

                long elapsed = System.currentTimeMillis() - stepStart;
                stepStart = System.currentTimeMillis();
                if (i == 0) firstStepDuration = elapsed;
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

            // ── Evict transformer, load VAE on-demand ──
            evictSession(transformerPath);  // ~6 GB freed
            batchedHidden = null; batchedPooled = null; // release embedding tensors

            // ── Decode with VAE ──
            // SD3 VAE: latent = latent / scaling_factor + shift_factor
            // scaling_factor=1.5305, shift_factor=0.0609
            request.reportProgress("Decoding latents with VAE…");
            OrtSession vae = getOrCreateSession(environment, vaeDecoderPath, sessionOptions);
            float[][][][] scaledLatents = new float[1][16][latentH][latentW];
            for (int c = 0; c < 16; c++)
                for (int y = 0; y < latentH; y++)
                    for (int x = 0; x < latentW; x++)
                        scaledLatents[0][c][y][x] = latents[0][c][y][x] / 1.5305f + 0.0609f;

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
            Path outputPath = writeOutputImage(image, "sd3");
            return InferenceResult.ok(
                    "Generated image for prompt: \"" + request.prompt() + "\"",
                    "SD 3.x pipeline completed (" + steps + " steps, CFG=" + guidanceScale
                            + ", shift=" + shiftFactor + ") | EP=" + provider
                            + (hasT5 ? " | T5 encoder active" : " | T5 skipped (CLIP-only)"),
                    outputPath.toString(), "image");
        } catch (Exception ex) {
            return InferenceResult.fail("SD 3.x pipeline failed: " + ex.getMessage());
        }
    }
}
