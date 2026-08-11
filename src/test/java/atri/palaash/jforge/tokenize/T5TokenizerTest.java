package atri.palaash.jforge.tokenize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class T5TokenizerTest {

    /** Valid minimal Unigram tokenizer with metaspace pieces. */
    private static final String TOKENIZER_JSON =
            "{\n" +
            "  \"model\": {\n" +
            "    \"type\": \"Unigram\",\n" +
            "    \"unk_id\": 2,\n" +
            "    \"vocab\": [\n" +
            "      [\"<pad>\", 0.0],\n" +
            "      [\"</s>\", 0.0],\n" +
            "      [\"<unk>\", -100.0],\n" +
            "      [\"\\u2581\", -5.0],\n" +
            "      [\"\\u2581h\", -1.0],\n" +
            "      [\"i\", -1.0],\n" +
            "      [\"\\u2581hello\", -1.0],\n" +
            "      [\"\\u2581world\", -1.0],\n" +
            "      [\"e\", -3.0],\n" +
            "      [\"l\", -3.0],\n" +
            "      [\"o\", -3.0],\n" +
            "      [\"w\", -3.0],\n" +
            "      [\"r\", -3.0],\n" +
            "      [\"d\", -3.0],\n" +
            "      [\"x\", -3.0]\n" +
            "    ]\n" +
            "  },\n" +
            "  \"added_tokens\": [\n" +
            "    {\"id\": 0, \"content\": \"<pad>\"},\n" +
            "    {\"id\": 1, \"content\": \"</s>\"},\n" +
            "    {\"id\": 2, \"content\": \"<unk>\"}\n" +
            "  ]\n" +
            "}";

    @Test
    void loadsAndEncodesKnownPieces(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("tokenizer.json");
        Files.writeString(p, TOKENIZER_JSON);
        T5Tokenizer tokenizer = T5Tokenizer.load(p);

        // "hello world" -> metaspace prepared: "▁hello▁world"
        long[] out = tokenizer.encode("hello world", 16);

        assertEquals(16, out.length);
        // Both words should be recognized as whole pieces (id 6 and 7).
        assertTrue(contains(out, 6), "expected '▁hello' piece in output");
        assertTrue(contains(out, 7), "expected '▁world' piece in output");
        // EOS (1) appended, then pad (0).
        assertTrue(contains(out, 1), "expected EOS token in output");
        assertEquals(0, out[out.length - 1], "tail should be pad");
    }

    @Test
    void emptyInputContainsEosThenPad(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("tokenizer.json");
        Files.writeString(p, TOKENIZER_JSON);
        T5Tokenizer tokenizer = T5Tokenizer.load(p);

        long[] out = tokenizer.encode("", 4);
        assertEquals(4, out.length);
        // viterbi of just "▁" -> [3], then eos (1), then pads (0)
        assertEquals(3, out[0]);
        assertEquals(1, out[1]);
        assertEquals(0, out[2]);
        assertEquals(0, out[3]);
    }

    @Test
    void unknownCharsFallBackToUnk(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("tokenizer.json");
        Files.writeString(p, TOKENIZER_JSON);
        T5Tokenizer tokenizer = T5Tokenizer.load(p);

        // 'x' is known; 'é' is not -> unk path exercised without failure
        long[] out = tokenizer.encode("xé", 8);
        assertEquals(8, out.length);
        assertTrue(contains(out, 2), "expected unk token for unknown char");
    }

    private static boolean contains(long[] arr, long value) {
        for (long t : arr) {
            if (t == value) {
                return true;
            }
        }
        return false;
    }
}