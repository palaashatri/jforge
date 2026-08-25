package atri.palaash.jforge.plugin;

import java.util.Set;

public record PluginDescriptor(String id, String version, String apiVersion, Set<String> capabilities, Set<String> permissions) {
    public PluginDescriptor {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("plugin id required");
        if (version == null) version = "1.0.0";
        if (apiVersion == null) apiVersion = "1.0";
        if (capabilities == null) capabilities = Set.of();
        if (permissions == null) permissions = Set.of();
        capabilities = Set.copyOf(capabilities);
        permissions = Set.copyOf(permissions);
    }
}
