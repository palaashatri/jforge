package atri.palaash.jforge.model.ingest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SafetensorsHeader {
    private final Map<String, TensorInfo> tensors;
    private final Map<String, String> metadata;
    private final long headerLength;

    public record TensorInfo(String dtype, List<Long> shape, long[] dataOffsets) {}

    private SafetensorsHeader(Map<String, TensorInfo> tensors, Map<String, String> metadata, long headerLength) {
        this.tensors = Collections.unmodifiableMap(new LinkedHashMap<>(tensors));
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.headerLength = headerLength;
    }

    public Map<String, TensorInfo> tensors() { return tensors; }
    public Map<String, String> metadata() { return metadata; }
    public long headerLength() { return headerLength; }
    public long tensorCount() { return tensors.size(); }
    public boolean hasTensor(String name) { return tensors.containsKey(name); }

    public static SafetensorsHeader parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    public static SafetensorsHeader parse(InputStream in) throws IOException {
        byte[] lenBytes = in.readNBytes(8);
        if (lenBytes.length < 8) throw new IOException("File too short for safetensors header");
        long headerLen = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
        if (headerLen < 2 || headerLen > 100_000_000) throw new IOException("Suspicious safetensors header length: " + headerLen);
        byte[] headerBytes = in.readNBytes((int) headerLen);
        if (headerBytes.length < headerLen) throw new IOException("Truncated safetensors header");
        String json = new String(headerBytes, java.nio.charset.StandardCharsets.UTF_8);
        return parseJson(json, headerLen);
    }

    public static SafetensorsHeader parseJson(String json, long headerLen) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> raw = mapper.readValue(json, new TypeReference<>() {});
        Map<String, TensorInfo> tensors = new LinkedHashMap<>();
        Map<String, String> metadata = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if ("__metadata__".equals(e.getKey())) {
                if (e.getValue() instanceof Map<?,?> m) {
                    for (Map.Entry<?,?> me : m.entrySet()) metadata.put(String.valueOf(me.getKey()), String.valueOf(me.getValue()));
                }
                continue;
            }
            if (!(e.getValue() instanceof Map<?,?> info)) continue;
            Object dtypeObj = info.get("dtype");
            Object shapeObj = info.get("shape");
            Object offsetsObj = info.get("data_offsets");
            String dtype = dtypeObj == null ? "unknown" : String.valueOf(dtypeObj);
            List<Long> shape = List.of();
            if (shapeObj instanceof List<?> list) {
                shape = list.stream().map(v -> ((Number)v).longValue()).toList();
            }
            long[] offsets = new long[0];
            if (offsetsObj instanceof List<?> list && list.size() == 2) {
                offsets = new long[]{ ((Number)list.get(0)).longValue(), ((Number)list.get(1)).longValue() };
            }
            tensors.put(e.getKey(), new TensorInfo(dtype, shape, offsets));
        }
        return new SafetensorsHeader(tensors, metadata, headerLen);
    }

    public static boolean looksLikeSafetensors(Path file) {
        try {
            if (!Files.isRegularFile(file)) return false;
            if (Files.size(file) < 8) return false;
            try (InputStream in = Files.newInputStream(file)) {
                byte[] lenBytes = in.readNBytes(8);
                long len = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
                return len >= 2 && len < 100_000_000;
            }
        } catch (Exception e) { return false; }
    }
}
