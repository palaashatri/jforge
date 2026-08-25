package atri.palaash.jforge.memory;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.engine.ModelBundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryManagerTest {
    @Test void estimationScalesWithResolution() {
        MemoryManager mm = new MemoryManager(8L*1024*1024*1024);
        ModelBundle bundle = ModelBundle.builder().id("test").minimumMemory("8GB").build();
        GenerationRequest small = GenerationRequest.builder().model("test").width(512).height(512).steps(20).build();
        GenerationRequest large = GenerationRequest.builder().model("test").width(1024).height(1024).steps(20).build();
        assertTrue(mm.estimate(bundle, large).estimatedBytes() > mm.estimate(bundle, small).estimatedBytes());
    }
    @Test void modeAffectsEstimate() {
        MemoryManager mm = new MemoryManager(16L*1024*1024*1024);
        ModelBundle b = ModelBundle.builder().id("test").build();
        GenerationRequest req = GenerationRequest.builder().model("test").build();
        mm.setMode(MemoryManager.MemoryMode.LOW);
        long low = mm.estimate(b, req).estimatedBytes();
        mm.setMode(MemoryManager.MemoryMode.PERFORMANCE);
        long perf = mm.estimate(b, req).estimatedBytes();
        assertTrue(perf > low);
    }
    @Test void trackingAndEviction() {
        MemoryManager mm = new MemoryManager(4L*1024*1024*1024);
        mm.trackLoaded("unet", 2L*1024*1024*1024);
        assertEquals(2L*1024*1024*1024, mm.usedBytes());
        mm.evict("unet");
        assertEquals(0, mm.usedBytes());
        mm.trackLoaded("a", 100); mm.trackLoaded("b", 200); mm.evictAll();
        assertEquals(0, mm.usedBytes());
    }
    @Test void parseMemoryStrings() {
        assertEquals(8L*1024*1024*1024, MemoryManager.parseMemory("8GB"));
        assertEquals((long)(1.5*1024*1024*1024), MemoryManager.parseMemory("1.5GB"));
        assertTrue(MemoryManager.parseMemory("unknown") > 0);
    }
}
