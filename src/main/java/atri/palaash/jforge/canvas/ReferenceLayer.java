package atri.palaash.jforge.canvas;

public record ReferenceLayer(String id, String name, int x, int y, int width, int height, float opacity, boolean visible,
                             String imagePath, float strength) implements Layer {
    public ReferenceLayer {
        if (id == null) id = Layer.newId();
        if (name == null) name = "Reference";
        if (imagePath == null) imagePath = "";
    }
    public ReferenceLayer(String imagePath, float strength) { this(Layer.newId(), "Reference", 0, 0, 256, 256, 0.5f, true, imagePath, strength); }
    @Override public Layer withPosition(int nx, int ny) { return new ReferenceLayer(id, name, nx, ny, width, height, opacity, visible, imagePath, strength); }
    @Override public Layer withSize(int nw, int nh) { return new ReferenceLayer(id, name, x, y, nw, nh, opacity, visible, imagePath, strength); }
    @Override public Layer withOpacity(float o) { return new ReferenceLayer(id, name, x, y, width, height, o, visible, imagePath, strength); }
    @Override public Layer withVisible(boolean v) { return new ReferenceLayer(id, name, x, y, width, height, opacity, v, imagePath, strength); }
}
