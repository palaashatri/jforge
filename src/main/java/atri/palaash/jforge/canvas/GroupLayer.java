package atri.palaash.jforge.canvas;

import java.util.List;

public record GroupLayer(String id, String name, int x, int y, int width, int height, float opacity, boolean visible,
                         List<Layer> children) implements Layer {
    public GroupLayer {
        if (id == null) id = Layer.newId();
        if (name == null) name = "Group";
        if (children == null) children = List.of();
        else children = List.copyOf(children);
    }
    public GroupLayer(String name, List<Layer> children) { this(Layer.newId(), name, 0, 0, 512, 512, 1f, true, children); }
    @Override public Layer withPosition(int nx, int ny) { return new GroupLayer(id, name, nx, ny, width, height, opacity, visible, children); }
    @Override public Layer withSize(int nw, int nh) { return new GroupLayer(id, name, x, y, nw, nh, opacity, visible, children); }
    @Override public Layer withOpacity(float o) { return new GroupLayer(id, name, x, y, width, height, o, visible, children); }
    @Override public Layer withVisible(boolean v) { return new GroupLayer(id, name, x, y, width, height, opacity, v, children); }
}
