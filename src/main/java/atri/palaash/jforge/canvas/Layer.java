package atri.palaash.jforge.canvas;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ImageLayer.class, name = "image"),
        @JsonSubTypes.Type(value = GenerationLayer.class, name = "generation"),
        @JsonSubTypes.Type(value = MaskLayer.class, name = "mask"),
        @JsonSubTypes.Type(value = ReferenceLayer.class, name = "reference"),
        @JsonSubTypes.Type(value = GuideLayer.class, name = "guide"),
        @JsonSubTypes.Type(value = GroupLayer.class, name = "group")
})
public sealed interface Layer permits ImageLayer, GenerationLayer, MaskLayer, ReferenceLayer, GuideLayer, GroupLayer {
    String id();
    String name();
    int x(); int y(); int width(); int height();
    float opacity();
    boolean visible();
    Layer withPosition(int nx, int ny);
    Layer withSize(int nw, int nh);
    Layer withOpacity(float o);
    Layer withVisible(boolean v);

    static String newId() { return UUID.randomUUID().toString(); }
}
