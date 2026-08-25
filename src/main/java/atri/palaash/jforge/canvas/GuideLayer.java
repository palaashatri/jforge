package atri.palaash.jforge.canvas;

public record GuideLayer(String id, String name, int x, int y, int width, int height, float opacity, boolean visible,
                         String guideType) implements Layer {
    public GuideLayer {
        if (id == null) id = Layer.newId();
        if (name == null) name = "Guide";
        if (guideType == null) guideType = "grid";
    }
    public GuideLayer(String guideType, int w, int h) { this(Layer.newId(), "Guide", 0, 0, w, h, 0.3f, true, guideType); }
    @Override public Layer withPosition(int nx, int ny) { return new GuideLayer(id, name, nx, ny, width, height, opacity, visible, guideType); }
    @Override public Layer withSize(int nw, int nh) { return new GuideLayer(id, name, x, y, nw, nh, opacity, visible, guideType); }
    @Override public Layer withOpacity(float o) { return new GuideLayer(id, name, x, y, width, height, o, visible, guideType); }
    @Override public Layer withVisible(boolean v) { return new GuideLayer(id, name, x, y, width, height, opacity, v, guideType); }
}
