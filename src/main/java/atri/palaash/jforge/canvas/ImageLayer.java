package atri.palaash.jforge.canvas;

public record ImageLayer(String id, String name, int x, int y, int width, int height, float opacity, boolean visible, String imagePath) implements Layer {
    public ImageLayer {
        if (id == null) id = Layer.newId();
        if (name == null) name = "Image";
        if (imagePath == null) imagePath = "";
    }
    public ImageLayer(String imagePath, int w, int h) { this(Layer.newId(), "Image", 0, 0, w, h, 1f, true, imagePath); }
    @Override public Layer withPosition(int nx, int ny) { return new ImageLayer(id, name, nx, ny, width, height, opacity, visible, imagePath); }
    @Override public Layer withSize(int nw, int nh) { return new ImageLayer(id, name, x, y, nw, nh, opacity, visible, imagePath); }
    @Override public Layer withOpacity(float o) { return new ImageLayer(id, name, x, y, width, height, o, visible, imagePath); }
    @Override public Layer withVisible(boolean v) { return new ImageLayer(id, name, x, y, width, height, opacity, v, imagePath); }
}
