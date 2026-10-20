package atri.palaash.jforge.canvas;

public record MaskLayer(String id, String name, int x, int y, int width, int height, float opacity, boolean visible,
                        String maskPath, float feather, int grow) implements Layer {
    public MaskLayer {
        if (id == null) id = Layer.newId();
        if (name == null) name = "Mask";
        if (maskPath == null) maskPath = "";
    }
    public MaskLayer(int w, int h) { this(Layer.newId(), "Mask", 0, 0, w, h, 1f, true, "", 0f, 0); }
    @Override public Layer withPosition(int nx, int ny) { return new MaskLayer(id, name, nx, ny, width, height, opacity, visible, maskPath, feather, grow); }
    @Override public Layer withSize(int nw, int nh) { return new MaskLayer(id, name, x, y, nw, nh, opacity, visible, maskPath, feather, grow); }
    @Override public Layer withOpacity(float o) { return new MaskLayer(id, name, x, y, width, height, o, visible, maskPath, feather, grow); }
    @Override public Layer withVisible(boolean v) { return new MaskLayer(id, name, x, y, width, height, opacity, v, maskPath, feather, grow); }
}
