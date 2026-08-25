package atri.palaash.jforge.comfy;

import atri.palaash.jforge.workflow.WorkflowGraph;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ComfyWorkflowTest {
    @Test void importSupportedAndUnsupported() throws Exception {
        String json = "{\"1\":{\"class_type\":\"CheckpointLoaderSimple\",\"inputs\":{\"ckpt_name\":\"sd15.safetensors\"}},\"2\":{\"class_type\":\"UnknownNode\",\"inputs\":{}}}";
        var r = ComfyWorkflow.importFromJson(json);
        assertEquals(1, r.graph.nodes().size());
        assertEquals(1, r.unsupportedNodes.size());
        assertTrue(r.warnings.stream().anyMatch(s -> s.contains("UnknownNode")));
    }
    @Test void exportRoundTrip() throws Exception {
        WorkflowGraph g = new WorkflowGraph();
        g.addNode("Prompt", Map.of("text", "a cat"));
        String json = ComfyWorkflow.exportToJson(g);
        assertTrue(json.contains("Prompt"));
        var r = ComfyWorkflow.importFromJson(json);
        assertEquals(1, r.graph.nodes().size());
    }
}
