package atri.palaash.jforge.ui;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A panel that lets users browse, search, save, and delete prompt presets.
 * <p>
 * Presets are persisted as a JSON array in {@code ~/.jforge-models/library.json}.
 * Each preset is a {@link PromptPreset} record containing a name, prompt text,
 * negative prompt, tags, and style.  A search box filters the visible list in
 * real time, and an "Apply" callback lets other parts of the app consume the
 * selected preset.
 */
public class PromptLibraryPanel extends JPanel {

    /** Path to the JSON file where presets are saved. */
    private static final Path PERSIST_PATH = Path.of(
            System.getProperty("user.home"), ".jforge-models", "library.json");
    /** Shared Jackson mapper for reading and writing the JSON library file. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** The Swing list model backing the visible JList. */
    private final DefaultListModel<PromptPreset> listModel = new DefaultListModel<>();
    /** The scrollable list that shows matching presets. */
    private final JList<PromptPreset> list = new JList<>(listModel);
    /** Text field the user types into to search presets. */
    private final JTextField searchField = new JTextField();
    /** Master list of every loaded preset (unfiltered). */
    private final List<PromptPreset> allPresets = new ArrayList<>();

    /** Build the panel with a search bar, preset list, and action buttons. */
    public PromptLibraryPanel() {
        super(new BorderLayout(8, 8));
        // Render each preset as "name | tags"
        list.setCellRenderer((lst, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.name() + "  |  " + value.tags()));
        list.setFixedCellHeight(24);

        // Top row: search label + text field
        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(new JLabel("Search"), BorderLayout.WEST);
        top.add(searchField, BorderLayout.CENTER);

        // Bottom row: Apply / Delete buttons
        JPanel buttons = new JPanel(new BorderLayout(6, 6));
        JButton applyButton = new JButton("Apply");
        JButton deleteButton = new JButton("Delete");
        buttons.add(applyButton, BorderLayout.WEST);
        buttons.add(deleteButton, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(320, 260));

        // Wire up live search as the user types
        searchField.getDocument().addDocumentListener((SimpleDocumentListener) e -> applyFilter());
        deleteButton.addActionListener(e -> deleteSelected());
        applyButton.addActionListener(e -> {
            PromptPreset preset = list.getSelectedValue();
            if (preset != null && onApply != null) {
                onApply.onApply(preset);
            }
        });
    }

    /** Lazily load presets from disk (call when tab becomes visible). */
    public void load() {
        if (!allPresets.isEmpty()) return;
        loadFromDisk();
    }

    /**
     * Callback interface invoked when the user clicks "Apply" on a selected preset.
     * The receiving component can then populate its prompt fields from the preset.
     */
    public interface ApplyListener {
        /**
         * Fired when a preset should be applied.
         *
         * @param preset the preset the user selected
         */
        void onApply(PromptPreset preset);
    }

    /** The currently-registered listener to call when "Apply" is clicked. */
    private ApplyListener onApply;

    /**
     * Register a listener to be notified when the user applies a preset.
     *
     * @param listener the callback, or {@code null} to clear
     */
    public void setOnApply(ApplyListener listener) {
        this.onApply = listener;
    }

    /**
     * Save a new preset to the library and refresh the displayed list.
     * The preset is inserted at the top so it appears immediately.
     *
     * @param preset the preset to persist
     */
    public void addPreset(PromptPreset preset) {
        allPresets.add(0, preset);
        applyFilter();
        saveToDisk();
    }

    /**
     * Filter the displayed presets by the current search query.
     * A preset matches if its name, prompt, or tags contain the query text
     * (case-insensitive).  An empty query shows every preset.
     */
    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        listModel.clear();
        for (PromptPreset preset : allPresets) {
            // Build a single search-able string from the fields the user cares about
            String haystack = (preset.name() + " " + preset.prompt() + " " + preset.tags()).toLowerCase();
            if (query.isEmpty() || haystack.contains(query)) {
                listModel.addElement(preset);
            }
        }
    }

    /** Remove the currently-highlighted preset from the library and persist the change. */
    private void deleteSelected() {
        PromptPreset selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        allPresets.remove(selected);
        applyFilter();
        saveToDisk();
    }

    /* ---- persistence ---- */

    /** Serialise every loaded preset to the JSON file on disk. */
    private void saveToDisk() {
        try {
            Files.createDirectories(PERSIST_PATH.getParent());
            List<Map<String, String>> list = new ArrayList<>();
            for (PromptPreset p : allPresets) {
                list.add(Map.of(
                        "name", p.name(),
                        "prompt", p.prompt(),
                        "negativePrompt", p.negativePrompt(),
                        "tags", p.tags(),
                        "style", p.style()
                ));
            }
            MAPPER.writeValue(PERSIST_PATH.toFile(), list);
        } catch (Exception ignored) {
            // Silently ignore — disk-full or permission errors are non-fatal
        }
    }

    /** Read saved presets from the JSON file and rebuild the in-memory list. */
    private void loadFromDisk() {
        try {
            if (!Files.exists(PERSIST_PATH)) { return; }
            List<Map<String, String>> list = MAPPER.readValue(
                    PERSIST_PATH.toFile(), new TypeReference<>() {});
            for (Map<String, String> m : list) {
                allPresets.add(new PromptPreset(
                        m.getOrDefault("name", ""),
                        m.getOrDefault("prompt", ""),
                        m.getOrDefault("negativePrompt", ""),
                        m.getOrDefault("tags", ""),
                        m.getOrDefault("style", "")
                ));
            }
            applyFilter();
        } catch (Exception ignored) {
            // Silently ignore — corrupt or missing files are non-fatal
        }
    }
}
