package atri.palaash.jforge.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PluginManagerTest {
    @Test void loadAndUnload(@TempDir Path tmp) {
        PluginContext ctx = new PluginContext(tmp, Logger.getLogger("test"));
        PluginManager mgr = new PluginManager(ctx);
        JForgePlugin p = new JForgePlugin() {
            @Override public PluginDescriptor descriptor() { return new PluginDescriptor("test.plugin", "1.0.0", "1.0", Set.of("TEXT_TO_IMAGE"), Set.of()); }
        };
        mgr.load(p);
        assertEquals(1, mgr.size());
        assertTrue(p.isCompatible("1.0"));
        mgr.unload("test.plugin");
        assertEquals(0, mgr.size());
    }

    @Test void faultyPluginDoesNotCrashManager(@TempDir Path tmp) {
        PluginContext ctx = new PluginContext(tmp, Logger.getLogger("test"));
        PluginManager mgr = new PluginManager(ctx);
        JForgePlugin bad = new JForgePlugin() {
            @Override public PluginDescriptor descriptor() { return new PluginDescriptor("bad", "1.0", "1.0", Set.of(), Set.of()); }
            @Override public void onLoad(PluginContext c) throws Exception { throw new RuntimeException("fail"); }
        };
        mgr.load(bad);
        assertEquals(0, mgr.size());
    }
}
