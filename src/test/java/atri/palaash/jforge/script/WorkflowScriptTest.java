package atri.palaash.jforge.script;

import atri.palaash.jforge.workflow.WorkflowGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowScriptTest {
    @Test void safeCheck() {
        assertTrue(WorkflowScript.isSafe(Map.of("nodes", List.of())));
        assertFalse(WorkflowScript.isSafe(Map.of("filesystem", true)));
        assertFalse(WorkflowScript.isSafe(Map.of("exec", "rm -rf")));
    }
    @Test void fromMap() {
        WorkflowGraph g = WorkflowScript.fromMap(Map.of("nodes", List.of(Map.of("type", "Prompt", "inputs", Map.of("text", "hi")))));
        assertEquals(1, g.nodes().size());
    }
}
