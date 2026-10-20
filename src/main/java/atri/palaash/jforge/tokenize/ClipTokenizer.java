package atri.palaash.jforge.tokenize;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal CLIP BPE (Byte-Pair Encoding) tokenizer for Stable Diffusion
 * text encoders.
 * <p>
 * Reads standard HuggingFace vocabulary (vocab.json) and merge (merges.txt)
 * files. Implements GPT-2 style pre-tokenisation (splitting on contractions,
 * letters, numbers, and punctuation), then applies BPE merge rules. Caches
 * recent BPE results in an LRU cache to accelerate repeated calls.
 *
 * <p>Immutable after {@link #load}; encode is thread-safe (the LRU cache is
 * unsynchronized, so callers serialize on encode — matching the original
 * monolith's usage).
 */
public final class ClipTokenizer implements TextTokenizer {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+");

    private final Map<String, Integer> vocab;
    private final Map<String, Integer> merges;
    private final Map<Integer, String> byteEncoder;
    private final Map<String, String> cache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 10_000;
        }
    };
    private final int bos;
    private final int eos;

    private ClipTokenizer(Map<String, Integer> vocab, Map<String, Integer> merges) {
        this.vocab = vocab;
        this.merges = merges;
        this.byteEncoder = bytesToUnicode();
        this.bos = vocab.getOrDefault("<|startoftext|>", 49406);
        this.eos = vocab.getOrDefault("<|endoftext|>", 49407);
    }

    /**
     * Load a CLIP tokenizer from HuggingFace-style JSON vocabulary and text
     * merges files.
     *
     * @param vocabPath  path to vocab.json
     * @param mergesPath path to merges.txt
     * @return a fully initialised tokenizer
     * @throws Exception if file reading or JSON parsing fails
     */
    public static ClipTokenizer load(Path vocabPath, Path mergesPath) throws Exception {
        Map<String, Integer> vocab = OBJECT_MAPPER.readValue(vocabPath.toFile(), new TypeReference<>() {
        });
        List<String> mergeLines = Files.readAllLines(mergesPath, StandardCharsets.UTF_8);
        Map<String, Integer> ranks = new HashMap<>();
        int rank = 0;
        for (String line : mergeLines) {
            String trimmed = line.trim();
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                continue;
            }
            ranks.put(trimmed, rank++);
        }
        return new ClipTokenizer(vocab, ranks);
    }

    @Override
    public long[] encode(String text, int maxLength) {
        List<Integer> ids = new ArrayList<>();
        ids.add(bos);
        String normalized = text == null ? "" : text.toLowerCase();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String token = matcher.group();
            StringBuilder encoded = new StringBuilder();
            byte[] bytes = token.getBytes(StandardCharsets.UTF_8);
            for (byte value : bytes) {
                encoded.append(byteEncoder.get(value & 0xFF));
            }
            String bpeToken = bpe(encoded.toString());
            for (String piece : bpeToken.split(" ")) {
                Integer id = vocab.get(piece);
                if (id != null) {
                    ids.add(id);
                }
            }
            if (ids.size() >= maxLength - 1) {
                break;
            }
        }
        ids.add(eos);
        long[] out = new long[maxLength];
        Arrays.fill(out, eos);
        for (int i = 0; i < Math.min(maxLength, ids.size()); i++) {
            out[i] = ids.get(i);
        }
        return out;
    }

    private String bpe(String token) {
        String cached = cache.get(token);
        if (cached != null) {
            return cached;
        }

        List<String> word = new ArrayList<>();
        for (int i = 0; i < token.length(); i++) {
            word.add(String.valueOf(token.charAt(i)));
        }
        if (word.size() == 1) {
            return token;
        }

        Set<String> pairs = getPairs(word);
        while (true) {
            String bestPair = null;
            int bestRank = Integer.MAX_VALUE;
            for (String pair : pairs) {
                int rank = merges.getOrDefault(pair, Integer.MAX_VALUE);
                if (rank < bestRank) {
                    bestRank = rank;
                    bestPair = pair;
                }
            }
            if (bestPair == null || !merges.containsKey(bestPair)) {
                break;
            }

            String[] parts = bestPair.split(" ");
            String first = parts[0];
            String second = parts[1];
            List<String> merged = new ArrayList<>();
            int i = 0;
            while (i < word.size()) {
                if (i < word.size() - 1 && word.get(i).equals(first) && word.get(i + 1).equals(second)) {
                    merged.add(first + second);
                    i += 2;
                } else {
                    merged.add(word.get(i));
                    i++;
                }
            }
            word = merged;
            if (word.size() == 1) {
                break;
            }
            pairs = getPairs(word);
        }

        String out = String.join(" ", word);
        cache.put(token, out);
        return out;
    }

    private static Set<String> getPairs(List<String> word) {
        Set<String> pairs = new HashSet<>();
        for (int i = 0; i < word.size() - 1; i++) {
            pairs.add(word.get(i) + " " + word.get(i + 1));
        }
        return pairs;
    }

    private static Map<Integer, String> bytesToUnicode() {
        List<Integer> bs = new ArrayList<>();
        for (int i = '!'; i <= '~'; i++) {
            bs.add(i);
        }
        for (int i = '¡'; i <= '¬'; i++) {
            bs.add(i);
        }
        for (int i = '®'; i <= 'ÿ'; i++) {
            bs.add(i);
        }

        List<Integer> cs = new ArrayList<>(bs);
        int n = 0;
        for (int b = 0; b < 256; b++) {
            if (!bs.contains(b)) {
                bs.add(b);
                cs.add(256 + n);
                n++;
            }
        }

        Map<Integer, String> map = new HashMap<>();
        for (int i = 0; i < bs.size(); i++) {
            map.put(bs.get(i), new String(Character.toChars(cs.get(i))));
        }
        return map;
    }
}