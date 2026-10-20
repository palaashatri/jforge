package atri.palaash.jforge.model.ingest;

import atri.palaash.jforge.engine.ModelBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelBundleFactoryTest {
    @Test void fromDiffusersDirectory(@TempDir Path tmp) throws Exception {
        String json = "{\"_class_name\":\"StableDiffusionXLPipeline\",\"_diffusers_version\":\"0.30.0\",\"unet\":[\"diffusers\",\"UNet2DConditionModel\"],\"vae\":[\"diffusers\",\"AutoencoderKL\"]}";
        Files.writeString(tmp.resolve("model_index.json"), json);
        Files.createDirectories(tmp.resolve("unet"));
        Files.writeString(tmp.resolve("unet").resolve("config.json"), "{}");
        ModelBundle b = ModelBundleFactory.fromDiffusersDirectory(tmp, "my-sdxl");
        assertEquals("my-sdxl", b.id());
        assertEquals("sdxl", b.family());
        assertTrue(b.components().containsKey("unet"));
        assertNotNull(b.defaultScheduler());
    }

    @Test void fromSafetensorsFile(@TempDir Path tmp) throws Exception {
        String headerJson = "{\"weight\":{\"dtype\":\"F16\",\"shape\":[4,4],\"data_offsets\":[0,32]}}";
        byte[] header = headerJson.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(header.length).array());
        out.write(header);
        out.write(new byte[32]);
        Path f = tmp.resolve("model.safetensors");
        Files.write(f, out.toByteArray());
        ModelBundle b = ModelBundleFactory.fromSafetensorsFile(f, "my-model");
        assertEquals("my-model", b.id());
        assertFalse(b.family().isBlank());
        assertEquals(1, b.components().size());
    }

    @Test void fromOnnxFile(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("model.onnx");
        Files.write(f, new byte[1024]);
        ModelBundle b = ModelBundleFactory.fromOnnxFile(f, "my-onnx");
        assertEquals("onnx", b.family());
        assertTrue(b.minimumMemory().contains("GB"));
    }
}
