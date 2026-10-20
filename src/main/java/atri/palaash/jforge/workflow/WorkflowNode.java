package atri.palaash.jforge.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.UUID;

public record WorkflowNode(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("inputs") Map<String, Object> inputs,
        @JsonProperty("outputs") Map<String, String> outputs
) {
    public WorkflowNode {
        if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
        if (type == null || type.isBlank()) throw new IllegalArgumentException("node type required");
        if (inputs == null) inputs = Map.of();
        if (outputs == null) outputs = Map.of();
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    public static WorkflowNode of(String type, Map<String, Object> inputs) {
        return new WorkflowNode(null, type, inputs, Map.of());
    }
}
