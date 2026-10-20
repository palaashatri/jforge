package atri.palaash.jforge.canvas;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class CanvasDocument {
    public static final String CURRENT_VERSION = "1.0";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @JsonProperty("version") private String version;
    @JsonProperty("width") private int width;
    @JsonProperty("height") private int height;
    @JsonProperty("layers") private List<Layer> layers;

    private final List<List<Layer>> undoStack = new ArrayList<>();
    private final List<List<Layer>> redoStack = new ArrayList<>();

    public CanvasDocument() { this(1024, 1024); }
    public CanvasDocument(int width, int height) {
        this.version = CURRENT_VERSION;
        this.width = width;
        this.height = height;
        this.layers = new ArrayList<>();
    }

    public String version() { return version; }
    public int width() { return width; }
    public int height() { return height; }
    public List<Layer> layers() { return Collections.unmodifiableList(layers); }
    public int layerCount() { return layers.size(); }

    public Optional<Layer> findById(String id) {
        return layers.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    public void addLayer(Layer layer) {
        pushUndo();
        layers.add(layer);
        redoStack.clear();
    }

    public boolean removeLayer(String id) {
        int idx = indexOf(id);
        if (idx < 0) return false;
        pushUndo();
        layers.remove(idx);
        redoStack.clear();
        return true;
    }

    public boolean moveLayer(String id, int nx, int ny) {
        int idx = indexOf(id);
        if (idx < 0) return false;
        pushUndo();
        layers.set(idx, layers.get(idx).withPosition(nx, ny));
        redoStack.clear();
        return true;
    }

    public boolean resizeLayer(String id, int nw, int nh) {
        int idx = indexOf(id);
        if (idx < 0) return false;
        pushUndo();
        layers.set(idx, layers.get(idx).withSize(nw, nh));
        redoStack.clear();
        return true;
    }

    public boolean setLayerVisible(String id, boolean visible) {
        int idx = indexOf(id);
        if (idx < 0) return false;
        pushUndo();
        layers.set(idx, layers.get(idx).withVisible(visible));
        redoStack.clear();
        return true;
    }

    public boolean reorderLayer(String id, int newIndex) {
        int idx = indexOf(id);
        if (idx < 0 || newIndex < 0 || newIndex >= layers.size()) return false;
        pushUndo();
        Layer l = layers.remove(idx);
        layers.add(newIndex, l);
        redoStack.clear();
        return true;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    public boolean undo() {
        if (!canUndo()) return false;
        redoStack.add(new ArrayList<>(layers));
        layers = new ArrayList<>(undoStack.remove(undoStack.size()-1));
        return true;
    }

    public boolean redo() {
        if (!canRedo()) return false;
        undoStack.add(new ArrayList<>(layers));
        layers = new ArrayList<>(redoStack.remove(redoStack.size()-1));
        return true;
    }

    public void saveTo(Path file) throws IOException {
        Persisted persist = new Persisted(version, width, height, layers);
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), persist);
    }

    public static CanvasDocument loadFrom(Path file) throws IOException {
        Persisted p = MAPPER.readValue(file.toFile(), Persisted.class);
        CanvasDocument doc = new CanvasDocument(p.width, p.height);
        doc.version = p.version != null ? p.version : CURRENT_VERSION;
        doc.layers = new ArrayList<>(p.layers != null ? p.layers : List.of());
        return doc;
    }

    private void pushUndo() {
        undoStack.add(new ArrayList<>(layers));
        if (undoStack.size() > 100) undoStack.remove(0);
    }

    private int indexOf(String id) {
        for (int i = 0; i < layers.size(); i++) if (layers.get(i).id().equals(id)) return i;
        return -1;
    }

    public record Persisted(String version, int width, int height, List<Layer> layers) {}
}
