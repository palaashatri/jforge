package atri.palaash.jforge.tokenize;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal Unigram (SentencePiece) tokenizer that reads a HuggingFace
 * {@code tokenizer.json} file. Supports the T5-XXL tokenizer used by
 * Stable Diffusion 3.x.
 *
 * <p>Implements NFKC normalisation, Metaspace pre-tokenisation (spaces →
 * ▁), Viterbi best-path segmentation over the Unigram log-prob vocabulary,
 * and special-token handling (pad / eos / unk).
 *
 * <p>Immutable after {@link #load}; encode is thread-safe.
 */
@SuppressWarnings("unchecked")
public final class T5Tokenizer implements TextTokenizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, Integer> pieceToId;
    private final float[] scores;
    private final int padId;
    private final int eosId;
    private final int unkId;
    private final int maxPieceLen;

    private T5Tokenizer(Map<String, Integer> pieceToId, float[] scores,
                        int padId, int eosId, int unkId, int maxPieceLen) {
        this.pieceToId = pieceToId;
        this.scores = scores;
        this.padId = padId;
        this.eosId = eosId;
        this.unkId = unkId;
        this.maxPieceLen = Math.max(1, maxPieceLen);
    }

    /**
     * Load a T5 tokenizer from a HuggingFace {@code tokenizer.json}.
     *
     * <p>Expected JSON structure (simplified):
     * <pre>{
     *   "model": {
     *     "type": "Unigram",
     *     "unk_id": 2,
     *     "vocab": [ ["▁", -1.23], ["s", -2.34], … ]
     *   },
     *   "added_tokens": [ {"id": 0, "content": "<pad>"}, … ]
     * }</pre>
     */
    public static T5Tokenizer load(Path tokenizerJsonPath) throws Exception {
        Map<String, Object> root = OBJECT_MAPPER.readValue(tokenizerJsonPath.toFile(), new TypeReference<>() {
        });
        Map<String, Object> modelSection = (Map<String, Object>) root.get("model");
        if (modelSection == null) {
            throw new IllegalArgumentException("tokenizer.json has no 'model' section");
        }
        List<List<Object>> vocab = (List<List<Object>>) modelSection.get("vocab");
        if (vocab == null || vocab.isEmpty()) {
            throw new IllegalArgumentException("tokenizer.json model has no 'vocab'");
        }

        int unkIdFromModel = modelSection.get("unk_id") instanceof Number n ? n.intValue() : 2;

        Map<String, Integer> pieceToId = new HashMap<>(vocab.size());
        float[] scoreArr = new float[vocab.size()];
        int maxLen = 1;
        for (int i = 0; i < vocab.size(); i++) {
            List<Object> entry = vocab.get(i);
            String piece = (String) entry.get(0);
            double score = entry.get(1) instanceof Number n ? n.doubleValue() : 0.0;
            pieceToId.put(piece, i);
            scoreArr[i] = (float) score;
            if (piece.length() > maxLen) {
                maxLen = piece.length();
            }
        }

        int padId = pieceToId.getOrDefault("<pad>", 0);
        int eosId = pieceToId.getOrDefault("</s>", 1);

        if (root.containsKey("added_tokens")) {
            List<Map<String, Object>> addedTokens =
                    (List<Map<String, Object>>) root.get("added_tokens");
            for (Map<String, Object> at : addedTokens) {
                String content = (String) at.get("content");
                int id = at.get("id") instanceof Number n ? n.intValue() : -1;
                if (content != null && id >= 0) {
                    pieceToId.put(content, id);
                    if ("<pad>".equals(content)) {
                        padId = id;
                    }
                    if ("</s>".equals(content)) {
                        eosId = id;
                    }
                }
            }
        }

        return new T5Tokenizer(pieceToId, scoreArr, padId, eosId, unkIdFromModel, maxLen);
    }

    @Override
    public long[] encode(String text, int maxLength) {
        String normalized = text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFKC);
        String prepared = "\u2581" + normalized.replace(' ', '\u2581');

        List<Integer> ids = viterbi(prepared);
        ids.add(eosId);

        long[] out = new long[maxLength];
        Arrays.fill(out, padId);
        for (int i = 0; i < Math.min(maxLength, ids.size()); i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    private List<Integer> viterbi(String text) {
        int n = text.length();
        float[] bestScore = new float[n + 1];
        int[] bestEnd = new int[n + 1];
        int[] bestPieceId = new int[n + 1];
        Arrays.fill(bestScore, Float.NEGATIVE_INFINITY);
        Arrays.fill(bestPieceId, unkId);
        bestScore[0] = 0;

        for (int i = 1; i <= n; i++) {
            int lo = Math.max(0, i - maxPieceLen);
            for (int j = lo; j < i; j++) {
                String sub = text.substring(j, i);
                Integer id = pieceToId.get(sub);
                if (id != null) {
                    float candidate = bestScore[j] + scores[id];
                    if (candidate > bestScore[i]) {
                        bestScore[i] = candidate;
                        bestEnd[i] = j;
                        bestPieceId[i] = id;
                    }
                }
            }
            if (bestScore[i] == Float.NEGATIVE_INFINITY) {
                bestScore[i] = bestScore[i - 1] + scores[unkId];
                bestEnd[i] = i - 1;
                bestPieceId[i] = unkId;
            }
        }

        List<Integer> ids = new ArrayList<>();
        int pos = n;
        while (pos > 0) {
            ids.add(bestPieceId[pos]);
            pos = bestEnd[pos];
        }
        Collections.reverse(ids);
        return ids;
    }
}