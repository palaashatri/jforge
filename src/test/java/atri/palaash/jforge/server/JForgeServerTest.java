package atri.palaash.jforge.server;

import atri.palaash.jforge.api.JForge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JForgeServerTest {
    @Test void versionAndModelsEndpoints(@TempDir Path tmp) throws Exception {
        try (JForge forge = JForge.create(tmp)) {
            try (JForgeServer server = new JForgeServer(forge, 0)) {
                server.start();
                int port = server.port();
                HttpClient client = HttpClient.newHttpClient();
                HttpResponse<String> v = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/version")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, v.statusCode());
                assertTrue(v.body().contains("JForge"));

                HttpResponse<String> m = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/models")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, m.statusCode());
                assertTrue(m.body().contains("models"));
            }
        }
    }

    @Test void workerRegistrySelects() {
        WorkerRegistry reg = new WorkerRegistry();
        assertEquals("local", reg.selectBest("sd15"));
        reg.register("gpu-1", "192.168.1.10:8080", "cuda:0");
        assertEquals("gpu-1", reg.selectBest("sd15"));
        assertEquals(1, reg.available().size());
        reg.setAvailable("gpu-1", false);
        assertEquals(0, reg.available().size());
    }
}
