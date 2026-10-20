package atri.palaash.jforge.lora;

import atri.palaash.jforge.model.ingest.SafetensorsHeader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class LoRAMetadata {
    private final String baseModel;
    private final String architecture;
    private final int networkDim;
    private final int networkAlpha;
    private final List<String> triggerWords;
    private final Map<String, String> rawMetadata;

    private LoRAMetadata(String baseModel, String arch, int dim, int alpha, List<String> triggers, Map<String,String> raw) {
        this.baseModel = baseModel;
        this.architecture = arch;
        this.networkDim = dim;
        this.networkAlpha = alpha;
        this.triggerWords = Collections.unmodifiableList(new ArrayList<>(triggers));
        this.rawMetadata = Collections.unmodifiableMap(raw);
    }

    public String baseModel() { return baseModel; }
    public String architecture() { return architecture; }
    public int networkDim() { return networkDim; }
    public int networkAlpha() { return networkAlpha; }
    public List<String> triggerWords() { return triggerWords; }
    public Map<String,String> rawMetadata() { return rawMetadata; }
    public boolean isValid() { return networkDim > 0 && !architecture.isBlank(); }

    public static LoRAMetadata fromHeader(SafetensorsHeader header) {
        Map<String,String> meta = header.metadata();
        String arch = meta.getOrDefault("modelspec.architecture", meta.getOrDefault("ss_network_module", "lora"));
        if (arch.contains("lora") || arch.isBlank()) {
            if (meta.containsKey("ss_base_model_version")) arch = meta.get("ss_base_model_version");
            else if (header.hasTensor("lora_unet_down_blocks_0_attentions_0_transformer_blocks_0_attn1_to_q.lora_down.weight")) arch = "sd15-lora";
            else if (header.hasTensor("lora_te_text_model_encoder_layers_0_self_attn_q_proj.lora_down.weight")) arch = "sdxl-lora";
            else arch = "unknown-lora";
        }
        int dim = parseInt(meta.get("ss_network_dim"), 0);
        int alpha = parseInt(meta.get("ss_network_alpha"), dim);
        List<String> triggers = new ArrayList<>();
        String t = meta.get("ss_tag_frequency");
        if (t != null && !t.isBlank()) triggers.add(t);
        String trainedWords = meta.get("modelspec.trigger_word");
        if (trainedWords != null) triggers.add(trainedWords);
        String base = meta.getOrDefault("ss_base_model_version", meta.getOrDefault("modelspec.base_model", "unknown"));
        return new LoRAMetadata(base, arch, dim, alpha, triggers, meta);
    }

    private static int parseInt(String s, int fb) {
        if (s == null) return fb;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fb; }
    }
}
