package atri.palaash.jforge.ui;

import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.ModelRegistry;
import atri.palaash.jforge.model.TaskType;
import atri.palaash.jforge.storage.DownloadProgress;
import atri.palaash.jforge.storage.ModelDownloader;
import atri.palaash.jforge.storage.ModelStorage;
import atri.palaash.jforge.storage.PyTorchToOnnxConverter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Panel for managing models: view the full model registry (table), download
 * ONNX models, import model files from disk, and convert PyTorch models to
 * ONNX format. Progress is shown via a progress bar and status label.
 */
public class ModelManagerPanel extends JPanel {

    private final ModelRegistry modelRegistry;
    private final ModelStorage modelStorage;
    private final ModelDownloader modelDownloader;
    private final ModelTableModel tableModel;
    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JTable table;
    private Runnable onModelsUpdated;

    /**
     * Constructs the Model Manager panel with a table of all registered models,
     * buttons for refresh, import, and download, and a status bar with progress.
     *
     * @param modelRegistry   the registry containing all known model descriptors
     * @param modelStorage    the storage backend for checking local availability
     * @param modelDownloader handles downloading models from remote sources
     */
    public ModelManagerPanel(ModelRegistry modelRegistry, ModelStorage modelStorage, ModelDownloader modelDownloader) {
        super(new BorderLayout(12, 12));
        this.modelRegistry = modelRegistry;
        this.modelStorage = modelStorage;
        this.modelDownloader = modelDownloader;
        this.tableModel = new ModelTableModel(modelRegistry.allModels(), modelStorage);
        this.statusLabel = new JLabel("Select a model and download if needed");
        this.statusLabel.setFont(this.statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        this.progressBar = new JProgressBar(0, 100);
        this.progressBar.setStringPainted(true);
        this.progressBar.setVisible(false);

        this.table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        JScrollPane tableScroll = new JScrollPane(table);

        JButton refreshButton = new JButton("Refresh from Hugging Face");
        refreshButton.setPreferredSize(new Dimension(200, 32));
        refreshButton.addActionListener(e -> refreshModelsFromHuggingFace());

        JButton importButton = new JButton("Import ONNX File");
        importButton.setPreferredSize(new Dimension(160, 32));
        importButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                statusLabel.setText("Select a row first.");
                return;
            }
            importModelFromFile(tableModel.modelAt(row), row);
        });

        JButton downloadButton = new JButton("Download / Redownload");
        downloadButton.setPreferredSize(new Dimension(190, 32));
        downloadButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                statusLabel.setText("Select a row first.");
                return;
            }
            ModelDescriptor descriptor = tableModel.modelAt(row);
            if (isPyTorchModel(descriptor)) {
                handlePyTorchDownload(descriptor, row);
            } else {
                startDownload(descriptor, row);
            }
        });

        /* Show hint when a PyTorch model row is selected */
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int row = table.getSelectedRow();
            if (row < 0) return;
            ModelDescriptor desc = tableModel.modelAt(row);
            if (isPyTorchModel(desc)) {
                statusLabel.setText("This is a PyTorch model - clicking Download will convert it to ONNX.");
            }
        });

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.add(refreshButton, BorderLayout.WEST);
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.add(importButton);
        rightActions.add(downloadButton);
        top.add(rightActions, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(progressBar, BorderLayout.EAST);

        add(bottom, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    }

    /**
     * Refreshes the table data from the model registry and updates the
     * local availability status for every model.
     */
    private void refreshTable() {
        tableModel.setRows(modelRegistry.allModels());
        tableModel.refreshAvailability();
        statusLabel.setText("Availability refreshed. Root: " + modelStorage.root());
    }

    /**
     * Check whether a model descriptor represents a PyTorch-only model
     * that requires conversion to ONNX.
     */
    private static boolean isPyTorchModel(ModelDescriptor desc) {
        return desc.sourceUrl().startsWith("hf-pytorch://")
                || PyTorchToOnnxConverter.isPyTorchModel(desc.id())
                || PyTorchToOnnxConverter.isPyTorchModel(desc.relativePath());
    }

    /**
     * Handle download of a PyTorch model: confirm with user, then convert to ONNX.
     */
    private void handlePyTorchDownload(ModelDescriptor descriptor, int row) {
        String modelId = descriptor.sourceUrl().startsWith("hf-pytorch://")
                ? descriptor.sourceUrl().substring("hf-pytorch://".length())
                : descriptor.id();

        int choice = JOptionPane.showConfirmDialog(this,
                "\"" + descriptor.displayName() + "\" is a PyTorch model.\n\n"
                + "JForge will automatically convert it to ONNX format\n"
                + "so it can run with the ONNX Runtime engine.\n\n"
                + "This requires Python 3 (dependencies are installed automatically).\n"
                + "The conversion may take several minutes.\n\n"
                + "Proceed with download and conversion?",
                "Convert PyTorch to ONNX",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            startConversion(modelId, descriptor.displayName(), "diffusers");
        }
    }

    /**
     * Fetches the latest list of available ONNX and PyTorch models from
     * Hugging Face and merges them into the local model registry.
     */
    public void refreshModelsFromHuggingFace() {
        statusLabel.setText("Refreshing models from Hugging Face (ONNX + PyTorch)...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        modelDownloader.discoverTextToImageModels()
                .whenComplete((models, error) -> SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    if (error != null) {
                        statusLabel.setText("Refresh failed: " + error.getMessage());
                        return;
                    }
                    int added = modelRegistry.mergeDownloadableAssets(models);
                    refreshTable();
                    statusLabel.setText("Model refresh complete. Added " + added + " new model(s).");
                    if (onModelsUpdated != null) {
                        onModelsUpdated.run();
                    }
                }));
    }

    /**
     * Registers a callback that is invoked whenever the set of available
     * models changes (download, import, conversion, or refresh).
     *
     * @param onModelsUpdated runnable to notify other panels of model changes
     */
    public void setOnModelsUpdated(Runnable onModelsUpdated) {
        this.onModelsUpdated = onModelsUpdated;
    }

    /**
     * Initiates downloading of a model, updating progress in the table and
     * progress bar. On completion, marks the model as available and notifies
     * listeners.
     *
     * @param descriptor the model to download
     * @param row        the table row index for updating availability
     */
    private void startDownload(ModelDescriptor descriptor, int row) {
        statusLabel.setText("Downloading " + descriptor.displayName() + "...");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setValue(0);
        tableModel.updateProgress(descriptor.id(), 0);
        modelDownloader.download(descriptor, progress -> onProgress(descriptor, progress))
                .whenComplete((path, error) -> SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setVisible(false);
                    if (error != null) {
                        statusLabel.setText("Download failed: " + error.getMessage());
                        return;
                    }
                    tableModel.setAvailable(row, true);
                    tableModel.updateProgress(descriptor.id(), 100);
                    statusLabel.setText("Downloaded: " + path);
                    if (onModelsUpdated != null) {
                        onModelsUpdated.run();
                    }
                }));
    }

    /**
     * Updates the progress bar and table with the latest download progress.
     *
     * @param descriptor the model being downloaded
     * @param progress   the current download progress state
     */
    private void onProgress(ModelDescriptor descriptor, DownloadProgress progress) {
        SwingUtilities.invokeLater(() -> {
            if (progress.isStatusMessage()) {
                statusLabel.setText(progress.statusMessage());
                return;
            }
            progressBar.setIndeterminate(false);
            progressBar.setValue(progress.percent());
            tableModel.updateProgress(descriptor.id(), progress.percent());
        });
    }

    /**
     * Opens a file chooser to let the user import a model file (.onnx, .pt, .pth)
     * and copies it to the correct model storage location. PyTorch files are
     * offered for conversion to ONNX first.
     *
     * @param descriptor the model descriptor to associate the imported file with
     * @param row        the table row index for updating availability
     */
    private void importModelFromFile(ModelDescriptor descriptor, int row) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select model file for " + descriptor.displayName());
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Model files (*.onnx, *.pt, *.pth)", "onnx", "pt", "pth"));
        int selection = chooser.showOpenDialog(this);
        if (selection != JFileChooser.APPROVE_OPTION) {
            statusLabel.setText("Import canceled.");
            return;
        }

        Path source = chooser.getSelectedFile().toPath();
        String fileName = source.getFileName().toString().toLowerCase();

        // If user imports a PyTorch file, offer to convert it to ONNX
        if (fileName.endsWith(".pt") || fileName.endsWith(".pth")) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "'" + source.getFileName() + "' is a PyTorch model file.\n\n"
                    + "Would you like to convert it to ONNX format?\n"
                    + "(Requires Python 3 with torch and onnx packages)",
                    "Convert to ONNX?",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                startConversion(source.toString(), source.getFileName().toString(), "generic");
                return;
            } else if (choice == JOptionPane.CANCEL_OPTION) {
                statusLabel.setText("Import canceled.");
                return;
            }
            // NO → import as-is (fall through)
        }

        Path target = modelStorage.modelPath(descriptor);
        try {
            modelStorage.ensureParentDirectory(descriptor);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            tableModel.setAvailable(row, true);
            tableModel.updateProgress(descriptor.id(), 100);
            statusLabel.setText("Imported: " + target);
            System.out.println("[JForge] Model imported: " + descriptor.displayName() + " from " + source);
            if (onModelsUpdated != null) {
                onModelsUpdated.run();
            }
        } catch (IOException ex) {
            System.out.println("[JForge] ERROR: Import failed for " + descriptor.displayName() + ": " + ex.getMessage());
            statusLabel.setText("Import failed: " + ex.getMessage());
        }
    }

    /* ── PyTorch → ONNX conversion ───────────────────────────────────── */

    /**
     * Launch the full conversion pipeline on a background thread.
     */
    private void startConversion(String modelId, String displayName, String mode) {
        // 1. Check for Python
        String python = PyTorchToOnnxConverter.findPython();
        if (python == null) {
            int choice = JOptionPane.showConfirmDialog(this,
                    "Python 3 is required for PyTorch \u2192 ONNX conversion\n"
                    + "but was not found on your system.\n\n"
                    + "Would you like to open the Python download page?",
                    "Python Not Found",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                PyTorchToOnnxConverter.openPythonDownloadPage();
            }
            return;
        }

        // 2. Check if model is likely gated — prompt for HF token
        String hfToken = null;
        if (PyTorchToOnnxConverter.isLikelyGatedModel(modelId)) {
            hfToken = (String) JOptionPane.showInputDialog(
                    this,
                    "'" + modelId + "' is a gated model that requires authentication.\n\n"
                    + "Steps to get a HuggingFace token:\n"
                    + "  1. Go to huggingface.co and sign in\n"
                    + "  2. Accept the model's license on its model page\n"
                    + "  3. Go to Settings \u2192 Access Tokens \u2192 Create new token\n\n"
                    + "Paste your HuggingFace access token:",
                    "Authentication Required",
                    JOptionPane.PLAIN_MESSAGE,
                    null, null, "");
            if (hfToken == null || hfToken.isBlank()) {
                statusLabel.setText("Conversion canceled \u2014 HuggingFace token is required for gated models.");
                return;
            }
        }

        // 3. Determine output directory
        String sanitized = modelId.replaceAll("[^a-zA-Z0-9._-]", "-").toLowerCase();
        Path outputDir = modelStorage.root().resolve("text-image").resolve("converted-" + sanitized);

        // 4. Show progress
        statusLabel.setText("Converting " + displayName + " to ONNX\u2026");
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        System.out.println("[JForge] Starting PyTorch \u2192 ONNX conversion: " + modelId);

        // 5. Run on background thread
        final String conversionMode = mode;
        final String token = hfToken;
        CompletableFuture.supplyAsync(() -> {
            PyTorchToOnnxConverter converter = new PyTorchToOnnxConverter(
                    msg -> SwingUtilities.invokeLater(() -> statusLabel.setText(msg))
            );
            return converter.convert(modelId, outputDir, conversionMode, token);
        }).whenComplete((path, error) -> SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setVisible(false);
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                if (cause instanceof PyTorchToOnnxConverter.PythonNotFoundException) {
                    int choice = JOptionPane.showConfirmDialog(this,
                            cause.getMessage() + "\n\nOpen the Python download page?",
                            "Python Not Found",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE);
                    if (choice == JOptionPane.YES_OPTION) {
                        PyTorchToOnnxConverter.openPythonDownloadPage();
                    }
                } else {
                    System.out.println("[JForge] ERROR: Conversion failed for " + modelId + ": " + cause.getMessage());
                    statusLabel.setText("Conversion failed: " + cause.getMessage());
                    JOptionPane.showMessageDialog(this,
                            "Conversion failed:\n" + cause.getMessage(),
                            "Conversion Error",
                            JOptionPane.ERROR_MESSAGE);
                }
                return;
            }
            registerConvertedModel(modelId, sanitized, path);
        }));
    }

    /**
     * After a successful conversion, register the new ONNX model in the registry
     * and refresh the table so it appears immediately.
     */
    private void registerConvertedModel(String modelId, String sanitized, Path outputDir) {
        // Determine the ONNX artifact path — SD 3.x uses transformer/, SD 1.x/SDXL uses unet/
        Path transformerOnnx = outputDir.resolve("transformer").resolve("model.onnx");
        Path unetOnnx = outputDir.resolve("unet").resolve("model.onnx");
        Path genericOnnx = outputDir.resolve("model.onnx");

        String relativePath;
        if (Files.exists(transformerOnnx)) {
            // SD 3.x model (MMDiT transformer)
            relativePath = "text-image/converted-" + sanitized + "/transformer/model.onnx";
        } else if (Files.exists(unetOnnx)) {
            relativePath = "text-image/converted-" + sanitized + "/unet/model.onnx";
        } else if (Files.exists(genericOnnx)) {
            relativePath = "text-image/converted-" + sanitized + "/model.onnx";
        } else {
            statusLabel.setText("Conversion produced output but no ONNX files were found.");
            return;
        }

        String id = "converted_" + sanitized.replace("-", "_");
        ModelDescriptor desc = new ModelDescriptor(
                id,
                "Converted: " + modelId,
                TaskType.TEXT_TO_IMAGE,
                relativePath,
                "",
                "Auto-converted from PyTorch via JForge."
        );

        modelRegistry.mergeDownloadableAssets(List.of(desc));
        refreshTable();
        statusLabel.setText("\u2713 Converted and registered: " + modelId);
        System.out.println("[JForge] Conversion complete: " + modelId + " \u2192 " + relativePath);
        if (onModelsUpdated != null) {
            onModelsUpdated.run();
        }
    }

    /**
     * Table model backing the model manager table. Shows each model's task type,
     * display name, file size, format (ONNX/PyTorch), availability, download
     * progress, and source URL.
     */
    private static final class ModelTableModel extends AbstractTableModel {

        private final String[] columns = {"Task", "Model", "Size", "Format", "Available", "Progress", "Source URL"};
        private final java.util.ArrayList<ModelDescriptor> rows;
        private final ModelStorage storage;
        private final Map<String, Integer> progressById = new HashMap<>();
        private final Map<String, Boolean> availableById = new HashMap<>();

        /**
         * Constructs the table model with the given rows and storage backend.
         *
         * @param rows    the initial list of model descriptors
         * @param storage the storage backend for checking availability
         */
        private ModelTableModel(List<ModelDescriptor> rows, ModelStorage storage) {
            this.rows = new java.util.ArrayList<>(rows);
            this.storage = storage;
            refreshAvailability();
        }

        /**
         * Replaces all rows in the model and refreshes availability flags.
         *
         * @param newRows the new list of model descriptors
         */
        private void setRows(List<ModelDescriptor> newRows) {
            rows.clear();
            rows.addAll(newRows);
            refreshAvailability();
        }

        /**
         * Returns the model descriptor at the given row index.
         *
         * @param row the row index
         * @return the model descriptor at that row
         */
        private ModelDescriptor modelAt(int row) {
            return rows.get(row);
        }

        /**
         * Queries the storage backend for each model's availability and
         * fires a table data changed event to refresh the UI.
         */
        private void refreshAvailability() {
            for (ModelDescriptor descriptor : rows) {
                availableById.put(descriptor.id(), storage.isAvailable(descriptor));
                progressById.putIfAbsent(descriptor.id(), 0);
            }
            fireTableDataChanged();
        }

        /**
         * Marks a specific row's model as available (or not) in the cache
         * and fires a row update event.
         *
         * @param row       the row index
         * @param available whether the model is now available locally
         */
        private void setAvailable(int row, boolean available) {
            ModelDescriptor descriptor = rows.get(row);
            availableById.put(descriptor.id(), available);
            fireTableRowsUpdated(row, row);
        }

        /**
         * Stores the download progress percentage for a model and fires a
         * table data changed event.
         *
         * @param modelId the model identifier
         * @param percent the download progress (0-100)
         */
        private void updateProgress(String modelId, int percent) {
            progressById.put(modelId, percent);
            fireTableDataChanged();
        }

        /**
         * Returns the total number of model rows.
         *
         * @return the row count
         */
        @Override
        public int getRowCount() {
            return rows.size();
        }

        /**
         * Returns the number of table columns (7).
         *
         * @return the column count
         */
        @Override
        public int getColumnCount() {
            return columns.length;
        }

        /**
         * Returns the display name of the column at the given index.
         *
         * @param column the column index
         * @return the column name string
         */
        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        /**
         * Returns the cell value for the given row and column.
         * Columns are: Task, Model, Size, Format, Available, Progress, Source URL.
         *
         * @param rowIndex    the row index
         * @param columnIndex the column index
         * @return the cell value (String)
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            ModelDescriptor descriptor = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> descriptor.taskType().displayName();
                case 1 -> descriptor.displayName();
                case 2 -> formatSize(descriptor.fileSizeBytes());
                case 3 -> isPyTorchModel(descriptor) ? "PyTorch (requires conversion)" : "ONNX";
                case 4 -> availableById.getOrDefault(descriptor.id(), false) ? "Yes" : "No";
                case 5 -> progressById.getOrDefault(descriptor.id(), 0) + "%";
                case 6 -> descriptor.sourceUrl();
                default -> "";
            };
        }

        /**
         * Formats a byte count into a human-readable size string (B, KB, MB, or GB).
         *
         * @param bytes the file size in bytes
         * @return a formatted string like "1.5 GB" or "?" for unknown size
         */
        private static String formatSize(long bytes) {
            if (bytes <= 0) return "?";
            if (bytes >= 1_000_000_000) return String.format("%.1f GB", bytes / 1_000_000_000.0);
            if (bytes >= 1_000_000) return String.format("%.0f MB", bytes / 1_000_000.0);
            if (bytes >= 1_000) return String.format("%.0f KB", bytes / 1_000.0);
            return bytes + " B";
        }
    }
}
