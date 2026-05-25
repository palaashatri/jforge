package atri.palaash.jforge.model;

public record ModelDescriptor(
        String id,
        String displayName,
        TaskType taskType,
        String relativePath,
        String sourceUrl,
        String notes,
        long fileSizeBytes
) {

    public ModelDescriptor(String id, String displayName, TaskType taskType,
                           String relativePath, String sourceUrl, String notes) {
        this(id, displayName, taskType, relativePath, sourceUrl, notes, 0);
    }
}
