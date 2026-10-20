package atri.palaash.jforge.workflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class WorkflowGraph {
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @JsonProperty("id") private String id;
    @JsonProperty("name") private String name;
    @JsonProperty("nodes") private List<WorkflowNode> nodes;
    @JsonProperty("edges") private List<WorkflowEdge> edges;
    @JsonProperty("version") private String version;

    public record WorkflowEdge(@JsonProperty("from") String from, @JsonProperty("fromOutput") String fromOutput,
                               @JsonProperty("to") String to, @JsonProperty("toInput") String toInput) {}

    public WorkflowGraph() { this(UUID.randomUUID().toString(), "Untitled", new ArrayList<>(), new ArrayList<>(), "1.0"); }

    public WorkflowGraph(String id, String name, List<WorkflowNode> nodes, List<WorkflowEdge> edges, String version) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "Untitled";
        this.nodes = nodes != null ? new ArrayList<>(nodes) : new ArrayList<>();
        this.edges = edges != null ? new ArrayList<>(edges) : new ArrayList<>();
        this.version = version != null ? version : "1.0";
    }

    public String id() { return id; }
    public String name() { return name; }
    public List<WorkflowNode> nodes() { return Collections.unmodifiableList(nodes); }
    public List<WorkflowEdge> edges() { return Collections.unmodifiableList(edges); }
    public String version() { return version; }

    public WorkflowNode addNode(String type, Map<String, Object> inputs) {
        WorkflowNode n = WorkflowNode.of(type, inputs);
        nodes.add(n);
        return n;
    }

    public void addEdge(String fromId, String fromOutput, String toId, String toInput) {
        edges.add(new WorkflowEdge(fromId, fromOutput, toId, toInput));
    }

    public void saveTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        MAPPER.writeValue(file.toFile(), this);
    }

    public static WorkflowGraph loadFrom(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), WorkflowGraph.class);
    }

    public String toJson() throws IOException { return MAPPER.writeValueAsString(this); }
    public static WorkflowGraph fromJson(String json) throws IOException { return MAPPER.readValue(json, WorkflowGraph.class); }
}
