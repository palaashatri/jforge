package atri.palaash.jforge.script;

import atri.palaash.jforge.workflow.WorkflowGraph;

import java.util.Map;

public final class WorkflowScript {
    public static WorkflowGraph fromMap(Map<String, Object> map) {
        WorkflowGraph g = new WorkflowGraph();
        Object nodes = map.get("nodes");
        if (nodes instanceof java.util.List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?,?> m) {
                    String type = String.valueOf(m.get("type"));
                    Map<String,Object> inputs = Map.of();
                    if (m.get("inputs") instanceof Map<?,?> im) {
                        java.util.HashMap<String,Object> hm = new java.util.HashMap<>();
                        for (Map.Entry<?,?> e : im.entrySet()) hm.put(String.valueOf(e.getKey()), e.getValue());
                        inputs = hm;
                    }
                    g.addNode(type, inputs);
                }
            }
        }
        return g;
    }

    public static Map<String, Object> toMap(WorkflowGraph g) {
        return Map.of("id", g.id(), "name", g.name(), "nodes", g.nodes().size(), "edges", g.edges().size(), "version", g.version());
    }

    public static boolean isSafe(Map<String, Object> script) {
        if (script.containsKey("filesystem") || script.containsKey("process") || script.containsKey("exec")) return false;
        return true;
    }
}
