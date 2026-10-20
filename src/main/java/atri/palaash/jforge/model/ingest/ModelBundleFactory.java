package atri.palaash.jforge.model.ingest;

import atri.palaash.jforge.api.Precision;
import atri.palaash.jforge.api.Quantization;
import atri.palaash.jforge.api.SchedulerType;
import atri.palaash.jforge.engine.ModelBundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModelBundleFactory {
    private ModelBundleFactory() {}

    public static ModelBundle fromDiffusersDirectory(Path root, String id) throws IOException {
        if (!DiffusersIndex.isDiffusersRoot(root)) throw new IOException("Not a Diffusers directory: missing model_index.json");
        DiffusersIndex index = DiffusersIndex.parse(root.resolve("model_index.json"));
        String family = index.architectureFamily();
        String arch = familyToArchitecture(family, index.pipelineClass());
        Map<String, String> components = new HashMap<>();
        for (String comp : index.components().keySet()) {
            if (index.components().get(comp) != null) components.put(comp, comp + "/model.safetensors");
        }
        SchedulerType scheduler = familyToScheduler(family);
        Set<SchedulerType> supported = familyToSupportedSchedulers(family);
        String license = readLicense(root);
        return ModelBundle.builder()
                .id(id)
                .displayName(id + " (" + index.pipelineClass() + ")")
                .architecture(arch)
                .family(family)
                .componentRoot(root.toString())
                .components(components)
                .defaultScheduler(scheduler)
                .supportedSchedulers(supported)
                .recommendedSteps(family.equals("sdxl") ? 30 : family.equals("sd3") ? 28 : 20)
                .recommendedCfg(family.equals("sdxl") || family.equals("sd3") ? 7.0 : 7.5)
                .license(license)
                .source(root.toString())
                .metadata(Map.of("pipeline_class", index.pipelineClass(), "diffusers_version", index.diffusersVersion()))
                .build();
    }

    public static ModelBundle fromSafetensorsFile(Path file, String id) throws IOException {
        SafetensorsHeader header = SafetensorsHeader.parse(file);
        String arch = inferArchFromHeader(header);
        String family = archToFamily(arch);
        long paramCount = estimateParams(header);
        String mem = paramCount > 8_000_000_000L ? "24GB" : paramCount > 3_000_000_000L ? "12GB" : "8GB";
        return ModelBundle.builder()
                .id(id)
                .displayName(id)
                .architecture(arch)
                .family(family)
                .componentRoot(file.getParent() == null ? "" : file.getParent().toString())
                .components(Map.of("model", file.getFileName().toString()))
                .defaultPrecision(Precision.FP16)
                .defaultQuantization(Quantization.NONE)
                .recommendedSteps(20)
                .minimumMemory(mem)
                .metadata(Map.of("tensor_count", String.valueOf(header.tensorCount()), "safetensors", "true"))
                .build();
    }

    public static ModelBundle fromOnnxFile(Path file, String id) throws IOException {
        long size = Files.size(file);
        String mem = size > 4_000_000_000L ? "16GB" : "8GB";
        return ModelBundle.builder()
                .id(id)
                .displayName(id)
                .architecture("onnx")
                .family("onnx")
                .componentRoot(file.getParent() == null ? "" : file.getParent().toString())
                .components(Map.of("model", file.getFileName().toString()))
                .minimumMemory(mem)
                .metadata(Map.of("onnx", "true", "file_size", String.valueOf(size)))
                .build();
    }

    private static String familyToArchitecture(String family, String pipelineClass) {
        return switch (family) {
            case "sdxl" -> "stable-diffusion-xl";
            case "sd3" -> "stable-diffusion-3";
            case "flux" -> "flux.1";
            case "sd15" -> "stable-diffusion-1.5";
            default -> pipelineClass == null || pipelineClass.isBlank() ? "unknown" : pipelineClass;
        };
    }

    private static String archToFamily(String arch) {
        if (arch.contains("flux")) return "flux";
        if (arch.contains("xl")) return "sdxl";
        if (arch.contains("3")) return "sd3";
        if (arch.contains("sd") || arch.contains("diffusion")) return "sd15";
        return "unknown";
    }

    private static String inferArchFromHeader(SafetensorsHeader h) {
        String meta = String.join(" ", h.metadata().values()).toLowerCase();
        if (meta.contains("flux")) return "flux.1";
        if (meta.contains("sdxl") || meta.contains("xl")) return "stable-diffusion-xl";
        if (meta.contains("sd3")) return "stable-diffusion-3";
        if (h.hasTensor("model.diffusion_model.layers.0.weight")) return "stable-diffusion-3";
        if (h.hasTensor("unet.down_blocks.0.attentions.0.transformer_blocks.0.attn1.to_q.weight")) return "sdxl";
        return "stable-diffusion-1.5";
    }

    private static long estimateParams(SafetensorsHeader h) {
        long total = 0;
        for (SafetensorsHeader.TensorInfo t : h.tensors().values()) {
            long elems = 1;
            for (Long d : t.shape()) elems *= d;
            total += elems;
        }
        return total;
    }

    private static SchedulerType familyToScheduler(String family) {
        return switch (family) {
            case "sd3", "flux" -> SchedulerType.FLOW_MATCH_EULER;
            case "sdxl" -> SchedulerType.EULER;
            default -> SchedulerType.DDIM;
        };
    }

    private static Set<SchedulerType> familyToSupportedSchedulers(String family) {
        return switch (family) {
            case "sd3", "flux" -> Set.of(SchedulerType.FLOW_MATCH_EULER, SchedulerType.EULER);
            case "sdxl" -> Set.of(SchedulerType.EULER, SchedulerType.EULER_ANCESTRAL, SchedulerType.DDIM, SchedulerType.DISTILLED_EULER);
            default -> Set.of(SchedulerType.DDIM, SchedulerType.EULER, SchedulerType.EULER_ANCESTRAL);
        };
    }

    private static String readLicense(Path root) {
        for (String name : List.of("LICENSE", "LICENSE.md", "LICENSE.txt", "README.md")) {
            Path p = root.resolve(name);
            if (Files.isRegularFile(p)) {
                try {
                    String t = Files.readString(p);
                    if (t.toLowerCase().contains("creativeml open rail")) return "CreativeML Open RAIL++-M";
                    if (t.toLowerCase().contains("apache 2.0")) return "Apache 2.0";
                    if (t.toLowerCase().contains("mit")) return "MIT";
                } catch (Exception ignored) {}
            }
        }
        return "unknown";
    }
}
