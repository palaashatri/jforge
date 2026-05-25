package atri.palaash.jforge.storage;

/**
 * Tracks the progress of a model download.
 * <p>
 * Think of this as a progress meter at a gas station: it tells you
 * how much has been downloaded so far ({@link #bytesRead()}),
 * the total expected size ({@link #totalBytes()}), and an optional
 * status message such as "retrying\u2026".
 *
 * @param bytesRead     the number of bytes downloaded so far (or -1 for status-only records)
 * @param totalBytes    the total number of bytes expected (or -1 if unknown)
 * @param statusMessage an optional human-readable message (may be {@code null})
 */
public record DownloadProgress(long bytesRead, long totalBytes, String statusMessage) {

    /** Standard progress constructor (no status message). */
    public DownloadProgress(long bytesRead, long totalBytes) {
        this(bytesRead, totalBytes, null);
    }

    /**
     * Calculates the download progress as a percentage (0 to 100).
     * <p>
     * If {@code totalBytes} is unknown (\u2264 0), returns 0 to avoid division by zero.
     *
     * @return an integer between 0 and 100 representing the percentage complete
     */
    public int percent() {
        if (totalBytes <= 0) {
            return 0;
        }
        long value = (bytesRead * 100) / totalBytes;
        return (int) Math.max(0, Math.min(100, value));
    }

    /** Returns true if this is a status/retry notification rather than normal progress. */
    public boolean isStatusMessage() {
        return statusMessage != null && !statusMessage.isEmpty();
    }
}
