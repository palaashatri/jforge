package atri.palaash.jforge.model.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiffusersIndexTest {
    @Test void parsesPipelineAndComponents() throws Exception {
        String json = """
                {"_class_name":"StableDiffusionXLPipeline","_diffusers_version":"0.30.0",
                 "scheduler":["diffusers","EulerDiscreteScheduler"],
                 "text_encoder":["transformers","CLIPTextModel"],
                 "unet":["diffusers","UNet2DConditionModel"],
                 "vae":["diffusers","AutoencoderKL"]}
                """;
        DiffusersIndex idx = DiffusersIndex.parseJson(json);
        assertEquals("StableDiffusionXLPipeline", idx.pipelineClass());
        assertEquals("sdxl", idx.architectureFamily());
        assertTrue(idx.hasComponent("unet"));
        assertEquals("diffusers", idx.components().get("unet").library());
    }

    @Test void architectureFamilyDetection() throws Exception {
        assertEquals("sd15", DiffusersIndex.parseJson("{\"_class_name\":\"StableDiffusionPipeline\"}").architectureFamily());
        assertEquals("sd3", DiffusersIndex.parseJson("{\"_class_name\":\"StableDiffusion3Pipeline\"}").architectureFamily());
        assertEquals("flux", DiffusersIndex.parseJson("{\"_class_name\":\"FluxPipeline\"}").architectureFamily());
    }

    @Test void detectsDiffusersRoot(@TempDir Path tmp) throws Exception {
        assertFalse(DiffusersIndex.isDiffusersRoot(tmp));
        Files.writeString(tmp.resolve("model_index.json"), "{\"_class_name\":\"StableDiffusionPipeline\"}");
        assertTrue(DiffusersIndex.isDiffusersRoot(tmp));
    }

    @Test void parsesFromFile(@TempDir Path tmp) throws Exception {
        String json = "{\"_class_name\":\"StableDiffusionPipeline\",\"unet\":[\"diffusers\",\"UNet2DConditionModel\"]}";
        Files.writeString(tmp.resolve("model_index.json"), json);
        DiffusersIndex idx = DiffusersIndex.parse(tmp.resolve("model_index.json"));
        assertEquals("StableDiffusionPipeline", idx.pipelineClass());
    }
}
