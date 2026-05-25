package atri.palaash.jforge.storage;

import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.TaskType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads AI models from the internet, with support for HuggingFace
 * repositories and direct ONNX file URLs.
 * <p>
 * Think of this as a download manager for AI models. It can resume
 * interrupted downloads, retry on failures, and automatically discover
 * companion files (weights, configs) needed by the model.
 */
public class ModelDownloader {

    /** Size of the I/O buffer used when streaming download data (16 KB). */
    private static final int BUFFER_SIZE = 16 * 1024;
    /** Regex to extract model IDs from HuggingFace API JSON responses. */
    private static final Pattern HF_MODEL_ID_PATTERN = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    /** Regex to extract file paths from HuggingFace API JSON responses. */
    private static final Pattern HF_RFILENAME_PATTERN = Pattern.compile("\\\"rfilename\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    /** HTTP client used for all network requests. */
    private final HttpClient httpClient;
    /** Local file-system storage that tracks where models are saved. */
    private final ModelStorage storage;
    /** Thread pool for asynchronous download operations. */
    private final Executor executor;

    /**
     * Creates a new downloader with the given HTTP client, storage backend,
     * and executor for async tasks.
     *
     * @param httpClient the HTTP client for network requests
     * @param storage    the local storage backend
     * @param executor   the thread pool for async downloads
     */
    public ModelDownloader(HttpClient httpClient, ModelStorage storage, Executor executor) {
        this.httpClient = httpClient;
        this.storage = storage;
        this.executor = executor;
    }

    /**
     * Checks whether a direct download URL is available for the given model.
     * Models with ONNX files, HuggingFace resolve URLs, or PyTorch-only
     * markers are considered downloadable.
     *
     * @param descriptor the model descriptor
     * @return {@code true} if the model can be downloaded directly
     */
    public boolean canDownload(ModelDescriptor descriptor) {
        String url = descriptor.sourceUrl();
        return url.contains(".onnx") || url.contains("/resolve/") || url.startsWith("hf-pytorch://");
    }

    /**
     * Downloads the model only if it is not already available on disk.
     * <p>
     * Like checking your closet before buying new clothes — if the
     * model is already stored locally, returns immediately.
     *
     * @param descriptor       the model descriptor
     * @param progressConsumer callback for download progress updates
     * @return a future that completes with the path to the downloaded model
     */
    public CompletableFuture<Path> downloadIfMissing(ModelDescriptor descriptor, Consumer<DownloadProgress> progressConsumer) {
        if (storage.isAvailable(descriptor)) {
            return CompletableFuture.completedFuture(storage.modelPath(descriptor));
        }
        return download(descriptor, progressConsumer);
    }

    /**
     * Downloads a model from its source URL to local storage.
     * <p>
     * This is the main download entry point. It streams the file to disk,
     * also fetches any companion artifacts (weight files, configs), and
     * cleans up partially-downloaded files if the operation fails.
     *
     * @param descriptor       the model descriptor
     * @param progressConsumer callback for download progress updates
     * @return a future that completes with the path to the downloaded model file
     */
    public CompletableFuture<Path> download(ModelDescriptor descriptor, Consumer<DownloadProgress> progressConsumer) {
        return CompletableFuture.supplyAsync(() -> {
            Path target = storage.modelPath(descriptor);
            List<Path> writtenFiles = new ArrayList<>();
            System.out.println("[JForge] Download started: " + descriptor.displayName());
            try {
                String downloadUrl = descriptor.sourceUrl();
                if (!downloadUrl.toLowerCase(Locale.ROOT).contains(".onnx")
                    && !downloadUrl.toLowerCase(Locale.ROOT).contains("/resolve/")) {
                    throw new IOException("No direct ONNX artifact URL configured for " + descriptor.displayName()
                            + ". Current source is a repository page. Configure a direct .onnx URL first.");
                }

                storage.ensureParentDirectory(descriptor);

                downloadUrlToPath(downloadUrl, target, progressConsumer);
                writtenFiles.add(target);
                // Download companion files (weights, configs) from HuggingFace if applicable
                downloadCompanionFilesIfNeeded(descriptor, target.getParent(), progressConsumer, writtenFiles);
                // Download known bundle files (UNet, text encoder, VAE) for well-known models
                downloadKnownBundleFiles(descriptor, progressConsumer, writtenFiles);

                System.out.println("[JForge] Download complete: " + descriptor.displayName()
                        + " (" + writtenFiles.size() + " file(s))");
                return target;
            } catch (Exception ex) {
                System.out.println("[JForge] ERROR: Download failed: " + descriptor.displayName()
                        + " — " + ex.getMessage());
                // Remove any partial files so we don't leave corrupt data on disk
                for (Path written : writtenFiles) {
                    try {
                        Files.deleteIfExists(written);
                    } catch (IOException ignored) {
                    }
                }
                throw new RuntimeException(ex);
            }
        }, executor);
    }

    /**
     * Downloads companion artifacts (weight files, binary data) that
     * accompany the main ONNX model from a HuggingFace repository.
     * <p>
     * Think of this as buying a puzzle and also picking up the
     * reference picture on the box — some models need extra files
     * to run correctly.
     *
     * @param descriptor       the model descriptor
     * @param targetDirectory  the directory where companion files will be saved
     * @param progressConsumer callback for download progress
     * @param writtenFiles     list to track all successfully written files (for cleanup on failure)
     * @throws IOException          if network or file I/O fails
     * @throws InterruptedException if the download thread is interrupted
     */
    private void downloadCompanionFilesIfNeeded(ModelDescriptor descriptor,
                                                Path targetDirectory,
                                                Consumer<DownloadProgress> progressConsumer,
                                                List<Path> writtenFiles) throws IOException, InterruptedException {
        String sourceUrl = descriptor.sourceUrl();
        HfResolvePath hfPath = parseHuggingFaceResolvePath(sourceUrl);
        if (hfPath == null) {
            return;
        }

        // Fetch the HuggingFace model API to discover available files
        String detailsBody = httpGetText("https://huggingface.co/api/models/" + hfPath.repoId());
        if (detailsBody.isBlank()) {
            return;
        }

        String folder = parentFolder(hfPath.filePath());
        Matcher matcher = HF_RFILENAME_PATTERN.matcher(detailsBody);
        while (matcher.find()) {
            String filePath = matcher.group(1);
            // Only consider files in the same folder, skip the main model file itself
            if (!filePath.startsWith(folder + "/") || filePath.equals(hfPath.filePath())) {
                continue;
            }
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
            // Only download known companion artifact types (.pb, .bin, .data, weight files)
            if (!isCompanionArtifact(fileName)) {
                continue;
            }
            String companionUrl = "https://huggingface.co/" + hfPath.repoId()
                    + "/resolve/" + hfPath.revision() + "/" + filePath;
            Path companionTarget = targetDirectory.resolve(fileName);
            downloadUrlToPath(companionUrl, companionTarget, progressConsumer);
            writtenFiles.add(companionTarget);
        }
    }

    /**
     * Downloads the full set of bundle files for well-known model types
     * (SD 1.5, SD Turbo, SDXL Turbo, SDXL Base). Each bundle includes the
     * UNet, text encoder(s), VAE decoder, scheduler config, and tokenizer files.
     * <p>
     * Like ordering a combo meal instead of individual items — these
     * models need multiple files to work, and this method knows exactly
     * which ones are needed for each model ID.
     *
     * @param descriptor       the model descriptor
     * @param progressConsumer callback for download progress
     * @param writtenFiles     list to track all successfully written files
     * @throws IOException          if network or file I/O fails
     * @throws InterruptedException if the download thread is interrupted
     */
    private void downloadKnownBundleFiles(ModelDescriptor descriptor,
                                          Consumer<DownloadProgress> progressConsumer,
                                          List<Path> writtenFiles) throws IOException, InterruptedException {
        if ("sd_v15_onnx".equals(descriptor.id())) {
            downloadSdBundle(descriptor, "text-image/stable-diffusion-v15",
                    List.of(
                            "unet/model.onnx", "unet/weights.pb",
                            "text_encoder/model.onnx",
                            "vae_decoder/model.onnx",
                            "scheduler/scheduler_config.json",
                            "tokenizer/merges.txt", "tokenizer/special_tokens_map.json",
                            "tokenizer/tokenizer_config.json", "tokenizer/vocab.json"
                    ), progressConsumer, writtenFiles);
        } else if ("sd_turbo_onnx".equals(descriptor.id())) {
            downloadSdBundle(descriptor, "text-image/sd-turbo",
                    List.of(
                            "unet/model.onnx",
                            "text_encoder/model.onnx",
                            "vae_decoder/model.onnx",
                            "tokenizer/merges.txt", "tokenizer/special_tokens_map.json",
                            "tokenizer/tokenizer_config.json", "tokenizer/vocab.json"
                    ), progressConsumer, writtenFiles);
        } else if ("sdxl_turbo_onnx".equals(descriptor.id())) {
            downloadSdBundle(descriptor, "text-image/sdxl-turbo",
                    List.of(
                            "unet/model.onnx", "unet/weights.pb",
                            "text_encoder/model.onnx",
                            "text_encoder_2/model.onnx", "text_encoder_2/weights.pb",
                            "vae_decoder/model.onnx", "vae_decoder/weights.pb",
                            "scheduler/scheduler_config.json",
                            "tokenizer/merges.txt", "tokenizer/vocab.json",
                            "tokenizer_2/merges.txt", "tokenizer_2/vocab.json"
                    ), progressConsumer, writtenFiles);
        } else if ("sdxl_base_onnx".equals(descriptor.id())) {
            // SDXL Base 1.0 — dual text encoders, large UNet
            downloadSdBundle(descriptor, "text-image/sdxl-base",
                    List.of(
                            "unet/model.onnx", "unet/model.onnx_data",
                            "text_encoder/model.onnx",
                            "text_encoder_2/model.onnx", "text_encoder_2/model.onnx_data",
                            "vae_decoder/model.onnx",
                            "scheduler/scheduler_config.json",
                            "tokenizer/merges.txt", "tokenizer/vocab.json",
                            "tokenizer_2/merges.txt", "tokenizer_2/vocab.json"
                    ), progressConsumer, writtenFiles);
        }
    }

    /**
     * Downloads a set of files for a Stable Diffusion model bundle.
     * <p>
     * Each file is downloaded from the HuggingFace repository into a
     * subdirectory under the storage root. Files that already exist
     * and have content are skipped to avoid re-downloading.
     *
     * @param descriptor       the model descriptor
     * @param localDir         the subdirectory under the storage root
     * @param files            list of relative file paths to download
     * @param progressConsumer callback for download progress
     * @param writtenFiles     list to track all successfully written files
     * @throws IOException          if network or file I/O fails
     * @throws InterruptedException if the download thread is interrupted
     */
    private void downloadSdBundle(ModelDescriptor descriptor, String localDir,
                                  List<String> files,
                                  Consumer<DownloadProgress> progressConsumer,
                                  List<Path> writtenFiles) throws IOException, InterruptedException {
        HfResolvePath hfPath = parseHuggingFaceResolvePath(descriptor.sourceUrl());
        if (hfPath == null) { return; }
        String repo = hfPath.repoId();
        String revision = hfPath.revision();
        for (String relative : files) {
            Path target = storage.root().resolve(localDir).resolve(relative);
            Files.createDirectories(target.getParent());
            // Skip files that already exist on disk (resume optimization)
            if (Files.exists(target) && Files.size(target) > 0) { continue; }
            String url = "https://huggingface.co/" + repo + "/resolve/" + revision + "/" + relative;
            downloadUrlToPath(url, target, progressConsumer);
            writtenFiles.add(target);
        }
    }

    /** Maximum number of retry attempts when a download stalls or fails. */
    private static final int MAX_RETRIES = 5;
    private static final long STALL_TIMEOUT_MS = 90_000; // 90 seconds with no data → stall

    /**
     * Download a URL to a local file with automatic resume and retry.
     * If the file already has partial content, we attempt an HTTP Range request
     * to pick up where we left off. On stall (no data for 90 s) or network
     * error, we retry up to 5 times with exponential back-off.
     */
    private void downloadUrlToPath(String downloadUrl,
                                   Path target,
                                   Consumer<DownloadProgress> progressConsumer) throws IOException, InterruptedException {
        IOException lastException = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                downloadUrlToPathOnce(downloadUrl, target, progressConsumer);
                return; // success
            } catch (IOException ex) {
                lastException = ex;
                if (attempt < MAX_RETRIES) {
                    long backoff = 2000L * attempt;
                    System.out.println("[JForge] WARN: Download stalled/failed for " + target.getFileName()
                            + ", retrying in " + (backoff / 1000) + "s (attempt "
                            + (attempt + 1) + "/" + MAX_RETRIES + ")");
                    if (progressConsumer != null) {
                        progressConsumer.accept(new DownloadProgress(-1, -1,
                                "Download stalled/failed, retrying in " + (backoff / 1000) + "s (attempt "
                                        + (attempt + 1) + "/" + MAX_RETRIES + ")…"));
                    }
                    Thread.sleep(backoff);
                }
            }
        }
        throw lastException;
    }

    /**
     * Performs a single attempt to download a URL to a local file.
     * Supports HTTP Range requests for resuming interrupted downloads
     * and validates binary content for ONNX files.
     *
     * @param downloadUrl      the URL to download from
     * @param target           the local file path to write to
     * @param progressConsumer callback for download progress
     * @throws IOException          if the download fails or content is invalid
     * @throws InterruptedException if the download thread is interrupted
     */
    private void downloadUrlToPathOnce(String downloadUrl,
                                       Path target,
                                       Consumer<DownloadProgress> progressConsumer) throws IOException, InterruptedException {
        // Check for a partial download we can resume.
        long existingBytes = Files.exists(target) ? Files.size(target) : 0;

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .GET();
        if (existingBytes > 0) {
            reqBuilder.header("Range", "bytes=" + existingBytes + "-");
        }

        HttpResponse<InputStream> response = httpClient.send(reqBuilder.build(),
                HttpResponse.BodyHandlers.ofInputStream());
        int statusCode = response.statusCode();

        boolean resuming = (statusCode == 206 && existingBytes > 0);
        if (!resuming) {
            existingBytes = 0; // server does not support Range — start over
        }
        if (statusCode != 200 && statusCode != 206) {
            throw new IOException("Download failed with HTTP status " + statusCode + " for URL " + downloadUrl);
        }

        // Only enforce binary-content checks for ONNX / weight files,
        // not for companion text assets (.json, .txt, etc.).
        String targetName = target.getFileName().toString().toLowerCase(Locale.ROOT);
        boolean expectBinary = targetName.endsWith(".onnx") || targetName.endsWith(".pb")
                || targetName.endsWith(".bin") || targetName.endsWith(".data")
                || targetName.endsWith(".onnx_data");

        if (expectBinary && !resuming) {
            String contentType = response.headers().firstValue("content-type").orElse("").toLowerCase(Locale.ROOT);
            if (contentType.contains("text/html")) {
                throw new IOException("Unexpected response content type for ONNX download: " + contentType);
            }
        }

        long contentLength = response.headers()
                .firstValue("content-length")
                .map(Long::parseLong)
                .orElse(-1L);
        long totalBytes = (contentLength > 0) ? existingBytes + contentLength : -1L;

        StandardOpenOption[] openOptions = resuming
                ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND}
                : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};

        try (InputStream inputStream = response.body();
             OutputStream outputStream = Files.newOutputStream(target, openOptions)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            long downloaded = existingBytes;
            int read;
            boolean firstChunkChecked = resuming; // skip binary check when resuming
            long lastDataTime = System.currentTimeMillis();

            while (true) {
                // Use available() + sleep to implement stall detection without
                // blocking forever inside read().
                if (inputStream.available() <= 0) {
                    // Attempt a timed read using a virtual thread we can interrupt on stall.
                    final InputStream is = inputStream;
                    final int[] readResult = {-1};
                    final IOException[] readError = {null};
                    Thread readThread = Thread.ofVirtual().start(() -> {
                        try {
                            readResult[0] = is.read(buffer);
                        } catch (IOException e) {
                            readError[0] = e;
                        }
                    });
                    try {
                        readThread.join(STALL_TIMEOUT_MS);
                    } catch (InterruptedException ie) {
                        readThread.interrupt();
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted.");
                    }
                    if (readThread.isAlive()) {
                        readThread.interrupt();
                        // Close the stream to unblock the read thread
                        try { is.close(); } catch (IOException ignored) {}
                        throw new IOException("Download stalled — no data received for "
                                + (STALL_TIMEOUT_MS / 1000) + " seconds.");
                    }
                    if (readError[0] != null) throw readError[0];
                    read = readResult[0];
                } else {
                    read = inputStream.read(buffer);
                }

                if (read < 0) { break; }

                if (!firstChunkChecked && read > 0 && expectBinary) {
                    String prefix = new String(buffer, 0, Math.min(read, 64)).trim().toLowerCase(Locale.ROOT);
                    if (prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.startsWith("{")) {
                        throw new IOException("Downloaded content does not look like a binary ONNX model.");
                    }
                    firstChunkChecked = true;
                }
                outputStream.write(buffer, 0, read);
                downloaded += read;
                lastDataTime = System.currentTimeMillis();
                if (progressConsumer != null) {
                    progressConsumer.accept(new DownloadProgress(downloaded, totalBytes));
                }
            }
        }
    }

    /**
     * Determines whether a file name corresponds to a companion artifact
     * (weight files, binary data) that should be downloaded alongside the
     * main ONNX model.
     *
     * @param fileName the file name to check
     * @return {@code true} if the file is a companion artifact
     */
    private boolean isCompanionArtifact(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pb")
                || lower.endsWith(".bin")
                || lower.endsWith(".data")
                || lower.endsWith(".onnx_data")
                || lower.contains("weight");
    }

    /**
     * Extracts the parent directory path from a file path string.
     * If there is no parent (file at root), returns an empty string.
     *
     * @param filePath the file path to extract the parent from
     * @return the parent folder path, or empty string if none
     */
    private String parentFolder(String filePath) {
        int slash = filePath.lastIndexOf('/');
        if (slash <= 0) {
            return "";
        }
        return filePath.substring(0, slash);
    }

    /**
     * Parses a HuggingFace resolve URL into its components: repository ID,
     * revision (branch/tag/commit), and file path.
     * <p>
     * For example:
     * {@code https://huggingface.co/runwayml/stable-diffusion-v1-5/resolve/main/unet/model.onnx}
     * becomes repoId={@code runwayml/stable-diffusion-v1-5}, revision={@code main},
     * filePath={@code unet/model.onnx}.
     *
     * @param sourceUrl the HuggingFace resolve URL to parse
     * @return the parsed path record, or {@code null} if the URL is not a valid HF resolve URL
     */
    private HfResolvePath parseHuggingFaceResolvePath(String sourceUrl) {
        String marker = "https://huggingface.co/";
        if (!sourceUrl.startsWith(marker) || !sourceUrl.contains("/resolve/")) {
            return null;
        }
        String tail = sourceUrl.substring(marker.length());
        int resolveIndex = tail.indexOf("/resolve/");
        if (resolveIndex <= 0) {
            return null;
        }
        String repoId = tail.substring(0, resolveIndex);
        String afterResolve = tail.substring(resolveIndex + "/resolve/".length());
        int slash = afterResolve.indexOf('/');
        if (slash <= 0 || slash >= afterResolve.length() - 1) {
            return null;
        }
        String revision = afterResolve.substring(0, slash);
        String filePath = afterResolve.substring(slash + 1);
        return new HfResolvePath(repoId, revision, filePath);
    }

    /** Record holding the parsed components of a HuggingFace resolve URL. */
    private record HfResolvePath(String repoId, String revision, String filePath) {
    }

    /**
     * Discovers text-to-image models available on HuggingFace by searching
     * for ONNX and PyTorch models across multiple query categories.
     * <p>
     * Think of this as a librarian scanning the shelves to find new books
     * that match your interests. Results include models with existing ONNX
     * artifacts as well as PyTorch models flagged for conversion.
     *
     * @return a future that completes with a list of discovered model descriptors
     */
    public CompletableFuture<List<ModelDescriptor>> discoverTextToImageModels() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Set<String> modelIds = fetchCandidateModelIds();
                List<ModelDescriptor> discovered = new ArrayList<>();
                for (String modelId : modelIds) {
                    String detailUrl = "https://huggingface.co/api/models/" + modelId;
                    String details = httpGetText(detailUrl);
                    if (details.isBlank()) continue;

                    // Check if the model is gated (requires authentication to access)
                    boolean isGated = details.contains("\"gated\"") &&
                            (details.contains("\"gated\":true") || details.contains("\"gated\":\"auto\"")
                            || details.contains("\"gated\": true") || details.contains("\"gated\": \"auto\""));

                    // ── Determine model type and create descriptor ──
                    boolean hasOnnxUnet = details.contains("unet/model.onnx");
                    boolean hasOnnxTransformer = details.contains("transformer/model.onnx");
                    boolean hasEsrgan = modelId.toLowerCase(Locale.ROOT).contains("esrgan")
                            || modelId.toLowerCase(Locale.ROOT).contains("realesrgan")
                            || modelId.toLowerCase(Locale.ROOT).contains("upscal");
                    // Detect PyTorch-only models (those that need conversion to ONNX)
                    boolean hasPyTorch = details.contains("model_index.json")
                            || details.contains("diffusion_pytorch_model")
                            || details.contains(".safetensors")
                            || details.contains("\"pipeline_tag\":\"text-to-image\"");
                    // Don't mark as PyTorch if it already has ONNX artifacts
                    boolean isPyTorchOnly = hasPyTorch && !hasOnnxUnet && !hasOnnxTransformer;

                    // ── Classify model type and create appropriate descriptor ──
                    if (hasEsrgan) {
                        // ESRGAN upscaler — check for .onnx file
                        boolean hasOnnx = details.contains(".onnx");
                        if (!hasOnnx && !isGated) continue;
                        String onnxFile = findEsrganOnnxFile(details);
                        if (onnxFile == null) onnxFile = "onnx/model.onnx"; // common path
                        String sourceUrl = "https://huggingface.co/" + modelId + "/resolve/main/" + onnxFile;
                        String id = "hf_" + modelId.toLowerCase(Locale.ROOT).replace('/', '_').replace('-', '_');
                        String relativePath = "text-image/huggingface/" + modelId.replace('/', '-') + "/" + onnxFile;
                        discovered.add(new ModelDescriptor(id,
                                "HF: " + modelId + " (ESRGAN ONNX)",
                                TaskType.IMAGE_UPSCALE, relativePath, sourceUrl,
                                "Discovered ESRGAN/upscaler from Hugging Face.",
                                findFileSize(details, onnxFile)));
                    } else if (hasOnnxUnet && !isGated) {
                        // ONNX SD model with UNet (Stable Diffusion 1.x / 2.x architecture)
                        String sourceUrl = "https://huggingface.co/" + modelId + "/resolve/main/unet/model.onnx";
                        if (!urlLooksAccessible(sourceUrl)) continue;
                        String id = "hf_" + modelId.toLowerCase(Locale.ROOT).replace('/', '_').replace('-', '_');
                        String displayName = "HF: " + modelId + " (UNet ONNX)";
                        String relativePath = "text-image/huggingface/" + modelId.replace('/', '-') + "/unet/model.onnx";
                        discovered.add(new ModelDescriptor(id, displayName,
                                TaskType.TEXT_TO_IMAGE, relativePath, sourceUrl,
                                "Discovered from Hugging Face ONNX listing.",
                                findFileSize(details, "unet/model.onnx")));
                    } else if (hasOnnxTransformer && !isGated) {
                        // ONNX SD3-type model with transformer (Stable Diffusion 3.x / Flux architecture)
                        String sourceUrl = "https://huggingface.co/" + modelId + "/resolve/main/transformer/model.onnx";
                        String id = "hf_" + modelId.toLowerCase(Locale.ROOT).replace('/', '_').replace('-', '_');
                        String displayName = "HF: " + modelId + " (Transformer ONNX)";
                        String relativePath = "text-image/huggingface/" + modelId.replace('/', '-') + "/transformer/model.onnx";
                        discovered.add(new ModelDescriptor(id, displayName,
                                TaskType.TEXT_TO_IMAGE, relativePath, sourceUrl,
                                "Discovered SD 3.x-style ONNX model from Hugging Face.",
                                findFileSize(details, "transformer/model.onnx")));
                    } else if (isPyTorchOnly) {
                        // PyTorch model — needs conversion to ONNX via the Python converter
                        String sourceUrl = "hf-pytorch://" + modelId;
                        String id = "hf_pt_" + modelId.toLowerCase(Locale.ROOT).replace('/', '_').replace('-', '_');
                        String gatedNote = isGated ? " 🔒" : "";
                        String displayName = "HF: " + modelId + " (PyTorch → convert)" + gatedNote;
                        String relativePath = "text-image/converted-" + modelId.replace('/', '-').toLowerCase() + "/unet/model.onnx";
                        discovered.add(new ModelDescriptor(id, displayName,
                                TaskType.TEXT_TO_IMAGE, relativePath, sourceUrl,
                                "PyTorch model — will be converted to ONNX on download."
                                + (isGated ? " Gated model — requires HF token." : "")));
                    }
                }
                return discovered;
            } catch (Exception ex) {
                throw new RuntimeException("Unable to refresh models from Hugging Face: " + ex.getMessage(), ex);
            }
        }, executor);
    }

    /**
     * Try to find the ONNX file path in an ESRGAN repo's file listing.
     */
    private String findEsrganOnnxFile(String detailsJson) {
        Matcher m = HF_RFILENAME_PATTERN.matcher(detailsJson);
        while (m.find()) {
            String f = m.group(1);
            if (f.endsWith(".onnx")) return f;
        }
        return null;
    }

    /**
     * Extract the file size (in bytes) for a given file within a HuggingFace
     * model from the API JSON response. Returns 0 if not found.
     */
    private static long findFileSize(String detailsJson, String rfilename) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(detailsJson);
            JsonNode siblings = root.get("siblings");
            if (siblings != null && siblings.isArray()) {
                for (JsonNode sibling : siblings) {
                    JsonNode name = sibling.get("rfilename");
                    JsonNode size = sibling.get("size");
                    if (name != null && size != null && rfilename.equals(name.asText())) {
                        long s = size.asLong(0);
                        return s > 0 ? s : 0;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    /**
     * Searches HuggingFace for models matching various text-to-image and
     * upscaling-related queries, returning a set of candidate model IDs.
     *
     * @return a set of HuggingFace model IDs
     * @throws IOException          if the network request fails
     * @throws InterruptedException if the thread is interrupted
     */
    private Set<String> fetchCandidateModelIds() throws IOException, InterruptedException {
        Set<String> ids = new LinkedHashSet<>();
        // Search across multiple categories to find relevant models
        List<String> queries = List.of(
                "text-to-image onnx",
                "stable diffusion onnx",
                "onnxruntime sd",
                "stable-diffusion pytorch",
                "stable-diffusion-xl",
                "stable-diffusion-3",
                "stable-diffusion v1",
                "realesrgan onnx",
                "esrgan onnx",
                "real-esrgan",
                "image super resolution onnx"
        );
        for (String query : queries) {
            String searchUrl = "https://huggingface.co/api/models?search=" +
                    java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8) +
                    "&limit=60";
            String body = httpGetText(searchUrl);
            Matcher matcher = HF_MODEL_ID_PATTERN.matcher(body);
            while (matcher.find()) {
                String id = matcher.group(1);
                if (id.contains("/") && !id.startsWith("datasets/") && !id.startsWith("spaces/")) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    /**
     * Performs an HTTP GET request and returns the response body as a string.
     * Returns empty string on non-2xx responses or null bodies.
     *
     * @param url the URL to fetch
     * @return the response body, or empty string on failure
     * @throws IOException          if the request fails
     * @throws InterruptedException if the thread is interrupted
     */
    private String httpGetText(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() > 299) {
            return "";
        }
        return response.body() == null ? "" : response.body();
    }

    /**
     * Checks whether a URL is accessible by sending a lightweight HEAD request.
     * Useful for pre-validating download URLs before committing to a full download.
     *
     * @param url the URL to check
     * @return {@code true} if the URL returns a 2xx or 3xx status code
     */
    private boolean urlLooksAccessible(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception ex) {
            return false;
        }
    }
}
