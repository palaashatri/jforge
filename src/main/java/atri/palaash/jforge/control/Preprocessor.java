package atri.palaash.jforge.control;

public enum Preprocessor {
    NONE("", false),
    CANNY("canny", true),
    DEPTH_MIDAS("depth_midas", true),
    DEPTH_LERES("depth_leres", true),
    OPENPOSE("openpose", true),
    SCRIBBLE("scribble", true),
    SEGMENTATION("seg", true),
    TILE("tile", false),
    REFERENCE("reference", false);

    private final String id;
    private final boolean needsPreview;
    Preprocessor(String id, boolean needsPreview) { this.id = id; this.needsPreview = needsPreview; }
    public String id() { return id; }
    public boolean needsPreview() { return needsPreview; }
    public static Preprocessor fromId(String id) {
        if (id == null) return NONE;
        for (Preprocessor p : values()) if (p.id.equalsIgnoreCase(id)) return p;
        return NONE;
    }
}
