package atri.palaash.jforge.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A panel that displays a scrollable table of past image-generation jobs.
 * <p>
 * History is persisted to a JSON file in {@code ~/.jforge-models/history.json}
 * so it survives application restarts.  Each row corresponds to one
 * {@link HistoryEntry} record.
 */
public class HistoryPanel extends JPanel {

    /** Path to the JSON file on disk where history rows are stored. */
    private static final Path PERSIST_PATH = Path.of(
            System.getProperty("user.home"), ".jforge-models", "history.json");
    /** Shared Jackson mapper, configured to produce pretty-printed JSON. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** The table model backing the visible JTable. */
    private final HistoryTableModel tableModel;

    /** Build the panel with a single scrollable table. */
    public HistoryPanel() {
        super(new BorderLayout());
        this.tableModel = new HistoryTableModel();
        JTable table = new JTable(tableModel);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    /** Lazily load history from disk (call after panel becomes visible). */
    public void load() {
        if (!tableModel.rows.isEmpty()) return;
        loadFromDisk();
    }

    /**
     * Append an entry to the history table and persist the updated list to disk.
     *
     * @param entry the new history row to add
     */
    public void addEntry(HistoryEntry entry) {
        tableModel.addEntry(entry);
        saveToDisk();
    }

    /* ---- persistence ---- */

    /** Serialise every history row to the JSON file on disk. */
    private void saveToDisk() {
        try {
            Files.createDirectories(PERSIST_PATH.getParent());
            // Convert each HistoryEntry into a plain map so Jackson can serialise it easily
            List<Map<String, Object>> list = new ArrayList<>();
            for (HistoryEntry e : tableModel.rows) {
                list.add(Map.of(
                        "timestamp", e.timestamp().toString(),
                        "model", e.model(),
                        "prompt", e.prompt(),
                        "negativePrompt", e.negativePrompt(),
                        "seed", e.seed(),
                        "batch", e.batch(),
                        "size", e.size(),
                        "style", e.style(),
                        "status", e.status(),
                        "outputPath", e.outputPath() == null ? "" : e.outputPath()
                ));
            }
            MAPPER.writeValue(PERSIST_PATH.toFile(), list);
        } catch (Exception ignored) {
            // Silently ignore — disk-full or permission errors are non-fatal
        }
    }

    /** Read previously-saved history rows from the JSON file and repopulate the table. */
    private void loadFromDisk() {
        try {
            if (!Files.exists(PERSIST_PATH)) { return; }
            // Deserialise the JSON file into a list of string-keyed maps
            List<Map<String, Object>> list = MAPPER.readValue(
                    PERSIST_PATH.toFile(), new TypeReference<>() {});
            for (Map<String, Object> m : list) {
                HistoryEntry entry = new HistoryEntry(
                        LocalDateTime.parse((String) m.get("timestamp")),
                        (String) m.get("model"),
                        (String) m.get("prompt"),
                        (String) m.getOrDefault("negativePrompt", ""),
                        ((Number) m.get("seed")).longValue(),
                        ((Number) m.get("batch")).intValue(),
                        (String) m.get("size"),
                        (String) m.getOrDefault("style", ""),
                        (String) m.get("status"),
                        (String) m.getOrDefault("outputPath", "")
                );
                tableModel.rows.add(entry);
            }
            // Notify the JTable that all rows have changed
            tableModel.fireTableDataChanged();
        } catch (Exception ignored) {
            // Silently ignore — corrupt or missing files are non-fatal
        }
    }

    /**
     * Table model backing the history JTable.
     * Each row is a {@link HistoryEntry} and columns correspond to the entry fields.
     */
    private static final class HistoryTableModel extends AbstractTableModel {

        /** Column header labels displayed in the table. */
        private final String[] columns = {
                "Time", "Model", "Prompt", "Negative", "Seed", "Batch", "Size", "Style", "Status", "Output"
        };
        /** All history rows in display order (newest first). */
        final List<HistoryEntry> rows = new ArrayList<>();
        /** Formatter used to render {@link LocalDateTime} as a short time string. */
        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        /**
         * Insert an entry at the top of the list and refresh the table.
         *
         * @param entry the history entry to add
         */
        void addEntry(HistoryEntry entry) {
            rows.add(0, entry);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        /**
         * Return the cell value at the given position.
         * <p>
         * The switch maps the column index to the corresponding field of the
         * {@link HistoryEntry}, formatting the timestamp with a short time pattern.
         *
         * @param rowIndex    the row being queried
         * @param columnIndex the column being queried
         * @return the formatted cell value, or an empty string for unknown columns
         */
        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            HistoryEntry entry = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> formatter.format(entry.timestamp());
                case 1 -> entry.model();
                case 2 -> entry.prompt();
                case 3 -> entry.negativePrompt();
                case 4 -> entry.seed();
                case 5 -> entry.batch();
                case 6 -> entry.size();
                case 7 -> entry.style();
                case 8 -> entry.status();
                case 9 -> entry.outputPath();
                default -> "";
            };
        }
    }
}
