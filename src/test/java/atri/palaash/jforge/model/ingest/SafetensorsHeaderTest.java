package atri.palaash.jforge.model.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SafetensorsHeaderTest {
    private static byte[] makeFile(String headerJson) throws IOException {
        byte[] header = headerJson.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(header.length).array());
        out.write(header);
        out.write(new byte[]{0,1,2,3});
        return out.toByteArray();
    }

    @Test void parsesHeaderAndTensors() throws Exception {
        String json = """
                {"__metadata__":{"format":"pt"},"weight":{"dtype":"F16","shape":[768,512],"data_offsets":[0,1572864]},"bias":{"dtype":"F32","shape":[768],"data_offsets":[1572864,1575936]}}
                """.trim();
        SafetensorsHeader h = SafetensorsHeader.parseJson(json, json.length());
        assertEquals(2, h.tensorCount());
        assertTrue(h.hasTensor("weight"));
        assertEquals("F16", h.tensors().get("weight").dtype());
        assertEquals(2, h.tensors().get("weight").shape().size());
        assertEquals("pt", h.metadata().get("format"));
    }

    @Test void roundTripsThroughFile(@TempDir Path tmp) throws Exception {
        String json = "{\"a\":{\"dtype\":\"F32\",\"shape\":[2,2],\"data_offsets\":[0,16]}}";
        Path f = tmp.resolve("model.safetensors");
        Files.write(f, makeFile(json));
        SafetensorsHeader h = SafetensorsHeader.parse(f);
        assertEquals(1, h.tensorCount());
        assertTrue(h.hasTensor("a"));
    }

    @Test void rejectsTruncatedHeader(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("bad.safetensors");
        Files.write(f, new byte[]{5,0,0,0,0,0,0,0, '{','}'});
        assertThrows(IOException.class, () -> SafetensorsHeader.parse(f));
    }

    @Test void looksLikeSafetensorsCheck(@TempDir Path tmp) throws Exception {
        String json = "{\"a\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}}";
        Path good = tmp.resolve("good.safetensors");
        Files.write(good, makeFile(json));
        assertTrue(SafetensorsHeader.looksLikeSafetensors(good));
        Path bad = tmp.resolve("bad.bin");
        Files.writeString(bad, "not safetensors");
        assertFalse(SafetensorsHeader.looksLikeSafetensors(bad));
    }
}
