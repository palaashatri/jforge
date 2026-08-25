package atri.palaash.jforge.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowGraphTest {
    @Test void buildAndSerialize(@TempDir Path tmp) throws Exception {
        WorkflowGraph g = new WorkflowGraph();
        var n1 = g.addNode("Prompt", Map.of("text", "a cat"));
        var n2 = g.addNode("Model", Map.of("id", "sd15"));
        g.addEdge(n1.id(), "output", n2.id(), "prompt");
        assertEquals(2, g.nodes().size());
        assertEquals(1, g.edges().size());
        String json = g.toJson();
        assertTrue(json.contains("Prompt"));
        WorkflowGraph loaded = WorkflowGraph.fromJson(json);
        assertEquals(2, loaded.nodes().size());
        Path file = tmp.resolve("wf.json");
        g.saveTo(file);
        assertTrue(Files.exists(file));
        WorkflowGraph fromFile = WorkflowGraph.loadFrom(file);
        assertEquals(g.id(), fromFile.id());
    }

    @Test void emptyGraph() {
        WorkflowGraph g = new WorkflowGraph();
        assertNotNull(g.id());
        assertEquals(0, g.nodes().size());
    }
}
