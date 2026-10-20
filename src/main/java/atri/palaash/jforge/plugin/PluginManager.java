package atri.palaash.jforge.plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PluginManager {
    private static final Logger LOG = Logger.getLogger(PluginManager.class.getName());
    private final List<JForgePlugin> plugins = new ArrayList<>();
    private final PluginContext context;

    public PluginManager(PluginContext context) { this.context = context; }

    public void load(JForgePlugin plugin) {
        try {
            plugin.onLoad(context);
            plugins.add(plugin);
            LOG.info("Loaded plugin: " + plugin.descriptor().id());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to load plugin " + plugin.descriptor().id(), e);
        }
    }

    public void unload(String id) {
        plugins.removeIf(p -> {
            if (p.descriptor().id().equals(id)) {
                try { p.onUnload(); } catch (Exception e) { LOG.log(Level.WARNING, "Unload failed for " + id, e); }
                return true;
            }
            return false;
        });
    }

    public List<JForgePlugin> all() { return Collections.unmodifiableList(plugins); }
    public int size() { return plugins.size(); }
}
