package atri.palaash.jforge.security;

import java.nio.file.Path;

public final class SecurityAudit {
    private SecurityAudit() {}

    public static boolean isSafePath(Path base, Path candidate) {
        try {
            Path normBase = base.toAbsolutePath().normalize();
            Path normCand = candidate.toAbsolutePath().normalize();
            return normCand.startsWith(normBase);
        } catch (Exception e) { return false; }
    }

    public static boolean isSafeArchiveEntry(String entryName) {
        if (entryName == null) return false;
        if (entryName.contains("..") || entryName.startsWith("/") || entryName.contains("\\")) return false;
        return true;
    }

    public static String sanitizeModelId(String id) {
        if (id == null) return "";
        return id.replaceAll("[^a-zA-Z0-9._-]", "-");
    }
}
