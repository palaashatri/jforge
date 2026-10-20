package atri.palaash.jforge.plugin;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class PluginContext {
    private final Path dataDir;
    private final Logger logger;
    private final Map<String, Object> services = new ConcurrentHashMap<>();

    public PluginContext(Path dataDir, Logger logger) {
        this.dataDir = dataDir;
        this.logger = logger;
    }

    public Path dataDir() { return dataDir; }
    public Logger logger() { return logger; }
    public void registerService(String name, Object service) { services.put(name, service); }
    public <T> T getService(String name, Class<T> type) { return type.cast(services.get(name)); }
}
