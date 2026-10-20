package atri.palaash.jforge.model.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class DiffusersIndex {
    private final String pipelineClass;
    private final String diffusersVersion;
    private final Map<String, ComponentRef> components;

    public record ComponentRef(String library, String className) {}

    private DiffusersIndex(String pipelineClass, String diffusersVersion, Map<String, ComponentRef> components) {
        this.pipelineClass = pipelineClass;
        this.diffusersVersion = diffusersVersion;
        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    public String pipelineClass() { return pipelineClass; }
    public String diffusersVersion() { return diffusersVersion; }
    public Map<String, ComponentRef> components() { return components; }
    public boolean hasComponent(String name) { return components.containsKey(name); }

    public String architectureFamily() {
        if (pipelineClass == null) return "unknown";
        String p = pipelineClass.toLowerCase();
        if (p.contains("flux")) return "flux";
        if (p.contains("sdxl") || p.contains("xl")) return "sdxl";
        if (p.contains("sd3") || p.contains("stable-diffusion-3") || p.contains("stablediffusion3")) return "sd3";
        if (p.contains("stable-diffusion") || p.contains("stablediffusion")) return "sd15";
        if (p.contains("controlnet")) return "controlnet";
        return "unknown";
    }

    public Set<String> requiredFiles() {
        java.util.HashSet<String> files = new java.util.HashSet<>();
        for (String comp : components.keySet()) {
            files.add(comp + "/config.json");
            files.add(comp + "/model.safetensors");
            files.add(comp + "/diffusion_pytorch_model.safetensors");
            files.add(comp + "/model.onnx");
        }
        files.add("model_index.json");
        return files;
    }

    public static DiffusersIndex parse(Path modelIndexJson) throws IOException {
        String json = Files.readString(modelIndexJson);
        return parseJson(json);
    }

    static DiffusersIndex parseJson(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> raw = mapper.readValue(json, new TypeReference<>() {});
        String pipelineClass = raw.get("_class_name") == null ? "" : String.valueOf(raw.get("_class_name"));
        String version = raw.get("_diffusers_version") == null ? "" : String.valueOf(raw.get("_diffusers_version"));
        Map<String, ComponentRef> comps = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            Object v = e.getValue();
            if (v instanceof java.util.List<?> list && list.size() == 2) {
                comps.put(e.getKey(), new ComponentRef(String.valueOf(list.get(0)), String.valueOf(list.get(1))));
            } else if (v == null) {
                comps.put(e.getKey(), null);
            }
        }
        return new DiffusersIndex(pipelineClass, version, comps);
    }

    public static boolean isDiffusersRoot(Path dir) {
        return Files.isRegularFile(dir.resolve("model_index.json"));
    }
}
