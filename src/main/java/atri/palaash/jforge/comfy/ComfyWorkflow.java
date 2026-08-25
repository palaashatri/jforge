package atri.palaash.jforge.comfy;

import atri.palaash.jforge.workflow.WorkflowGraph;
import atri.palaash.jforge.workflow.WorkflowNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ComfyWorkflow {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class ImportResult {
        public final WorkflowGraph graph;
        public final List<String> warnings;
        public final List<String> unsupportedNodes;
        public ImportResult(WorkflowGraph g, List<String> warnings, List<String> unsupported) {
            this.graph = g; this.warnings = warnings; this.unsupportedNodes = unsupported;
        }
    }

    public static ImportResult importFromJson(String json) throws IOException {
        Map<String, Object> raw = MAPPER.readValue(json, new TypeReference<>() {});
        WorkflowGraph g = new WorkflowGraph();
        List<String> warnings = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            String nodeId = e.getKey();
            if (!(e.getValue() instanceof Map<?,?> node)) continue;
            Object classType = node.get("class_type");
            String type = classType == null ? "unknown" : String.valueOf(classType);
            String mapped = mapComfyType(type);
            if (mapped == null) {
                unsupported.add(type);
                warnings.add("Unsupported node: " + type + " (id " + nodeId + ")");
                continue;
            }
            Map<String, Object> inputs = Map.of();
            Object in = node.get("inputs");
            if (in instanceof Map<?,?> m) {
                java.util.HashMap<String,Object> hm = new java.util.HashMap<>();
                for (Map.Entry<?,?> me : m.entrySet()) hm.put(String.valueOf(me.getKey()), me.getValue());
                inputs = hm;
            }
            WorkflowNode n = new WorkflowNode(nodeId, mapped, inputs, Map.of());
            g.addNode(mapped, inputs);
            if (!type.equals(mapped)) warnings.add("Mapped " + type + " -> " + mapped);
        }
        return new ImportResult(g, warnings, unsupported);
    }

    public static String exportToJson(WorkflowGraph g) throws IOException {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        for (WorkflowNode n : g.nodes()) {
            out.put(n.id(), Map.of("class_type", n.type(), "inputs", n.inputs()));
        }
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
    }

    private static String mapComfyType(String comfy) {
        return switch (comfy) {
            case "CheckpointLoaderSimple", "UNETLoader", "Model" -> "Model";
            case "CLIPTextEncode", "Prompt" -> "Prompt";
            case "EmptyLatentImage", "Latent" -> "Latent";
            case "KSampler", "SamplerCustom", "Sampler" -> "Sampler";
            case "VAEDecode", "VAE" -> "VAE";
            case "SaveImage", "Export" -> "Export";
            case "LoraLoader", "LoRA" -> "LoRA";
            case "ControlNetLoader", "ControlNetApply", "Control" -> "Control";
            default -> null;
        };
    }
}
