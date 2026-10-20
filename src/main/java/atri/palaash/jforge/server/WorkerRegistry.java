package atri.palaash.jforge.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WorkerRegistry {
    public record WorkerInfo(String id, String address, String device, long lastSeen, boolean available) {}

    private final Map<String, WorkerInfo> workers = new ConcurrentHashMap<>();

    public void register(String id, String address, String device) {
        workers.put(id, new WorkerInfo(id, address, device, System.currentTimeMillis(), true));
    }

    public void heartbeat(String id) {
        WorkerInfo w = workers.get(id);
        if (w != null) workers.put(id, new WorkerInfo(w.id(), w.address(), w.device(), System.currentTimeMillis(), true));
    }

    public void setAvailable(String id, boolean available) {
        WorkerInfo w = workers.get(id);
        if (w != null) workers.put(id, new WorkerInfo(w.id(), w.address(), w.device(), w.lastSeen(), available));
    }

    public void remove(String id) { workers.remove(id); }

    public List<WorkerInfo> all() { return Collections.unmodifiableList(new ArrayList<>(workers.values())); }

    public List<WorkerInfo> available() { return workers.values().stream().filter(WorkerInfo::available).toList(); }

    public String selectBest(String modelId) {
        var avail = available();
        if (avail.isEmpty()) return "local";
        return avail.get(0).id();
    }
}
