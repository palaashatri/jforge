package atri.palaash.jforge.tokenize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipTokenizerTest {

    @Test
    void encodesAndPadsToMaxLength(@TempDir Path dir) throws Exception {
        // Minimal vocabulary in the GPT-2/CLIP BPE shape:
        // "<|startoftext|>" is 49406, "<|endoftext|>" is 49407, plus a few
        // byte-encoded pieces. Use vocab.json as a Map<String,Integer>.
        Map<String, Integer> vocab = new HashMap<>();
        vocab.put("<|startoftext|>", 49406);
        vocab.put("<|endoftext|>", 49407);
        // Byte-level pieces for "a", "p" etc. get encoded through bytesToUnicode,
        // so plain single characters that are printable ASCII map to "a" = "a".
        vocab.put("a", 1);
        vocab.put("p", 2);
        vocab.put("e", 3);
        vocab.put("l", 4);
        vocab.put("apple", 5);

        Path vocabPath = dir.resolve("vocab.json");
        Path mergesPath = dir.resolve("merges.txt");
        Files.writeString(vocabPath,
                "{\"<|startoftext|>\":49406,\"<|endoftext|>\":49407,\"a\":1,\"p\":2,\"e\":3,\"l\":4,\"apple\":5}");
        Files.writeString(mergesPath, "a p\np l\nl e\n");

        ClipTokenizer tokenizer = ClipTokenizer.load(vocabPath, mergesPath);
        long[] out = tokenizer.encode("apple", 8);

        // [BOS, apple..., EOS, pad, pad, pad, pad, pad]
        assertEquals(8, out.length);
        assertEquals(49406, out[0]);
        assertEquals(49407, out[out.length - 1]);
        // Some middle ground should contain 'apple' id (5) if BPE formed it, otherwise
        // the pieces. Either way tokens are in vocab.
        for (long token : out) {
            assertTrue(token == 49406 || token == 49407 || token >= 1, "unexpected token id: " + token);
        }
    }

    @Test
    void truncatesLongInputToMaxLength(@TempDir Path dir) throws Exception {
        Map<String, Integer> vocab = new HashMap<>();
        vocab.put("<|startoftext|>", 49406);
        vocab.put("<|endoftext|>", 49407);
        vocab.put("h", 1);
        vocab.put("i", 2);

        Path vocabPath = dir.resolve("vocab.json");
        Path mergesPath = dir.resolve("merges.txt");
        StringBuilder vocabJson = new StringBuilder();
        vocabJson.append('{');
        int n = 0;
        for (Map.Entry<String, Integer> e : vocab.entrySet()) {
            if (n++ > 0) {
                vocabJson.append(',');
            }
            vocabJson.append('"').append(e.getKey().replace("\"", "\\\"")).append("\":").append(e.getValue());
        }
        vocabJson.append('}');
        Files.writeString(vocabPath, vocabJson.toString());
        Files.writeString(mergesPath, "h i\n");

        ClipTokenizer tokenizer = ClipTokenizer.load(vocabPath, mergesPath);
        String longText = "hi".repeat(200);
        long[] out = tokenizer.encode(longText, 8);
        assertEquals(8, out.length);
        assertEquals(49406, out[0]);
    }

    @Test
    void emptyInputStillReturnsBosEos(@TempDir Path dir) throws Exception {
        Map<String, Integer> vocab = new HashMap<>();
        vocab.put("<|startoftext|>", 49406);
        vocab.put("<|endoftext|>", 49407);

        Path vocabPath = dir.resolve("vocab.json");
        Path mergesPath = dir.resolve("merges.txt");
        Files.writeString(vocabPath, "{\"<|startoftext|>\":49406,\"<|endoftext|>\":49407}");
        Files.writeString(mergesPath, "");

        ClipTokenizer tokenizer = ClipTokenizer.load(vocabPath, mergesPath);
        long[] out = tokenizer.encode("", 4);
        assertEquals(4, out.length);
        assertEquals(49406, out[0]);
        assertEquals(49407, out[1]);
        assertTrue(Arrays.equals(new long[]{49406, 49407, 49407, 49407}, out));
    }
}