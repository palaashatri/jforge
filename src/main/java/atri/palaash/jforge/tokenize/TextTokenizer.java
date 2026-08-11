package atri.palaash.jforge.tokenize;

/**
 * Contract for a text tokenizer used by a text encoder. Implementations
 * must be immutable and thread-safe.
 */
public interface TextTokenizer {

    /**
     * Encode text into token IDs, padded/truncated to {@code maxLength}.
     *
     * @param text      the input text (may be null or empty)
     * @param maxLength maximum sequence length
     * @return token IDs of length {@code maxLength}
     */
    long[] encode(String text, int maxLength);
}