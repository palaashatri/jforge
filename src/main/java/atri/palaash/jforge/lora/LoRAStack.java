package atri.palaash.jforge.lora;

import atri.palaash.jforge.api.LoRAConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoRAStack {
    private final List<LoRAConfig> loras = new ArrayList<>();

    public void add(LoRAConfig cfg) {
        validate(cfg);
        loras.add(cfg);
    }

    public void remove(String modelId) { loras.removeIf(c -> c.modelId().equals(modelId)); }

    public void reorder(int from, int to) {
        if (from < 0 || from >= loras.size() || to < 0 || to >= loras.size()) throw new IndexOutOfBoundsException();
        LoRAConfig c = loras.remove(from);
        loras.add(to, c);
    }

    public void setEnabled(String modelId, boolean enabled) {
        for (int i = 0; i < loras.size(); i++) {
            LoRAConfig c = loras.get(i);
            if (c.modelId().equals(modelId)) loras.set(i, new LoRAConfig(c.modelId(), c.strength(), enabled));
        }
    }

    public void setStrength(String modelId, double strength) {
        if (strength < 0 || strength > 2.0) throw new IllegalArgumentException("strength must be in [0,2]");
        for (int i = 0; i < loras.size(); i++) {
            LoRAConfig c = loras.get(i);
            if (c.modelId().equals(modelId)) loras.set(i, new LoRAConfig(c.modelId(), strength, c.enabled()));
        }
    }

    public List<LoRAConfig> all() { return Collections.unmodifiableList(loras); }
    public List<LoRAConfig> enabled() { return loras.stream().filter(LoRAConfig::enabled).toList(); }
    public int size() { return loras.size(); }
    public void clear() { loras.clear(); }

    public static void validate(LoRAConfig cfg) {
        if (cfg.modelId().isBlank()) throw new IllegalArgumentException("LoRA modelId must not be blank");
        if (cfg.strength() < 0 || cfg.strength() > 2.0) throw new IllegalArgumentException("LoRA strength out of range [0,2]: " + cfg.strength());
    }

    public static boolean isCompatible(String baseModelFamily, LoRAMetadata meta) {
        if (meta == null || !meta.isValid()) return false;
        String archFam = familyOf(meta.architecture());
        String baseFam = familyOf(baseModelFamily);
        return archFam.equals(baseFam) && !"unknown".equals(archFam);
    }

    private static String familyOf(String s) {
        String lower = s.toLowerCase();
        if (lower.contains("flux")) return "flux";
        if (lower.contains("sdxl") || lower.contains("xl")) return "sdxl";
        if (lower.contains("sd3") || lower.contains("sd 3") || lower.contains("stable-diffusion-3") || lower.contains("stablediffusion3")) return "sd3";
        if (lower.contains("sd15") || lower.contains("sd 1.5") || lower.contains("stable-diffusion-1.5") || lower.contains("stablediffusion1.5") || lower.contains("sd") || lower.contains("diffusion")) return "sd15";
        return "unknown";
    }
}
