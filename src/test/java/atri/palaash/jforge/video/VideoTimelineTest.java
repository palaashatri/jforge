package atri.palaash.jforge.video;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VideoTimelineTest {
    @Test void addAndDuration() {
        VideoTimeline tl = new VideoTimeline();
        tl.addClip(new VideoTimeline.Clip("c1", "a.mp4", 0, 2.5, 24));
        tl.addClip(new VideoTimeline.Clip("c2", "b.mp4", 2.5, 1.5, 24));
        assertEquals(2, tl.clipCount());
        assertEquals(4.0, tl.durationSec(), 1e-9);
        tl.removeClip("c1");
        assertEquals(1, tl.clipCount());
        tl.clear();
        assertEquals(0, tl.clipCount());
    }
    @Test void encodeWritesFile(@TempDir Path tmp) throws Exception {
        VideoTimeline tl = new VideoTimeline();
        tl.addClip(new VideoTimeline.Clip("c1", "a.mp4", 0, 1.0, 30));
        Path out = tmp.resolve("out").resolve("video.json");
        VideoEncoder.encode(tl, new VideoEncoder.EncodeRequest(out, "h264", 512, 512, 30, 4000));
        assertTrue(Files.exists(out));
        assertTrue(VideoEncoder.probe(out).containsKey("exists"));
    }
}
