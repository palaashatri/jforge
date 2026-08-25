package atri.palaash.jforge.canvas;

import java.util.List;

public record GenerationLayer(String id, String name, int x, int y, int width, int height, float opacity, boolean visible,
                              String prompt, String negativePrompt, long seed, String modelId, List<String> variantPaths) implements Layer {
    public GenerationLayer {
        if (id == null) id = Layer.newId();
        if (name == null) name = "Generation";
        if (prompt == null) prompt = "";
        if (negativePrompt == null) negativePrompt = "";
        if (modelId == null) modelId = "";
        if (variantPaths == null) variantPaths = List.of();
        else variantPaths = List.copyOf(variantPaths);
    }
    public GenerationLayer(String prompt, String modelId, int w, int h) { this(Layer.newId(), "Generation", 0, 0, w, h, 1f, true, prompt, "", 0L, modelId, List.of()); }
    @Override public Layer withPosition(int nx, int ny) { return new GenerationLayer(id, name, nx, ny, width, height, opacity, visible, prompt, negativePrompt, seed, modelId, variantPaths); }
    @Override public Layer withSize(int nw, int nh) { return new GenerationLayer(id, name, x, y, nw, nh, opacity, visible, prompt, negativePrompt, seed, modelId, variantPaths); }
    @Override public Layer withOpacity(float o) { return new GenerationLayer(id, name, x, y, width, height, o, visible, prompt, negativePrompt, seed, modelId, variantPaths); }
    @Override public Layer withVisible(boolean v) { return new GenerationLayer(id, name, x, y, width, height, opacity, v, prompt, negativePrompt, seed, modelId, variantPaths); }
}
