package atri.palaash.jforge.video;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class VideoEncoder {
    public record EncodeRequest(Path outputPath, String codec, int width, int height, double fps, int bitrateKbps) {}
    public static void encode(VideoTimeline timeline, EncodeRequest req) throws IOException {
        Files.createDirectories(req.outputPath().getParent());
        String json = String.format("{\"codec\":\"%s\",\"width\":%d,\"height\":%d,\"fps\":%.2f,\"duration\":%.2f,\"clips\":%d}",
                req.codec(), req.width(), req.height(), req.fps(), timeline.durationSec(), timeline.clipCount());
        Files.writeString(req.outputPath(), json);
    }
    public static Map<String, Object> probe(Path file) throws IOException {
        if (!Files.exists(file)) throw new IOException("Not found: " + file);
        return Map.of("exists", true, "size", Files.size(file));
    }
}
