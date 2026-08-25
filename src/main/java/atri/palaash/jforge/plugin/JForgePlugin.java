package atri.palaash.jforge.plugin;

public interface JForgePlugin {
    PluginDescriptor descriptor();
    default void onLoad(PluginContext context) throws Exception {}
    default void onUnload() throws Exception {}
    default boolean isCompatible(String apiVersion) { return descriptor().apiVersion().equals(apiVersion); }
}
