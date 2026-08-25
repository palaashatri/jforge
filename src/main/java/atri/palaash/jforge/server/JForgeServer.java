package atri.palaash.jforge.server;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.api.JForge;
import atri.palaash.jforge.model.ModelRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public final class JForgeServer implements AutoCloseable {
    private final HttpServer server;
    private final JForge forge;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int port;

    public JForgeServer(JForge forge, int port) throws IOException {
        this.forge = forge;
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/version", this::handleVersion);
        server.createContext("/api/models", this::handleModels);
        server.createContext("/api/generate", this::handleGenerate);
        server.createContext("/api/status", this::handleStatus);
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }
    @Override public void close() { stop(); }
    public int port() { return server.getAddress().getPort(); }

    private void handleVersion(HttpExchange ex) throws IOException {
        sendJson(ex, 200, Map.of("version", "1.0.0", "name", "JForge"));
    }

    private void handleModels(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { sendJson(ex, 405, Map.of("error", "GET only")); return; }
        ModelRegistry reg = forge.registry();
        var list = reg.allModels().stream().map(d -> Map.of("id", d.id(), "name", d.displayName(), "task", d.taskType().name())).toList();
        sendJson(ex, 200, Map.of("models", list));
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        sendJson(ex, 200, Map.of("status", "ok", "backend", "onnx-runtime"));
    }

    private void handleGenerate(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { sendJson(ex, 405, Map.of("error", "POST only")); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        try {
            Map<String, Object> req = mapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            String model = (String) req.getOrDefault("model", "");
            String prompt = (String) req.getOrDefault("prompt", "");
            int steps = req.get("steps") instanceof Number n ? n.intValue() : 20;
            GenerationRequest genReq = GenerationRequest.builder().model(model).prompt(prompt).steps(steps).build();
            GenerationResult result = forge.generate(genReq);
            if (result.success()) {
                sendJson(ex, 200, Map.of("success", true, "images", result.images().stream().map(i -> i.path().toString()).toList()));
            } else {
                sendJson(ex, 200, Map.of("success", false, "error", result.error()));
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            sendJson(ex, 400, Map.of("error", msg));
        }
    }

    private void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(obj);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
