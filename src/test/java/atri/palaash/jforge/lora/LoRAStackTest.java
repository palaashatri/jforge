package atri.palaash.jforge.lora;

import atri.palaash.jforge.api.LoRAConfig;
import atri.palaash.jforge.model.ingest.SafetensorsHeader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LoRAStackTest {
    @Test void addAndReorder() {
        LoRAStack stack = new LoRAStack();
        stack.add(new LoRAConfig("lora-a", 1.0));
        stack.add(new LoRAConfig("lora-b", 0.8));
        assertEquals(2, stack.size());
        stack.reorder(1, 0);
        assertEquals("lora-b", stack.all().get(0).modelId());
    }

    @Test void enableDisableAndStrength() {
        LoRAStack stack = new LoRAStack();
        stack.add(new LoRAConfig("lora-a", 1.0, true));
        stack.setEnabled("lora-a", false);
        assertEquals(0, stack.enabled().size());
        stack.setEnabled("lora-a", true);
        assertEquals(1, stack.enabled().size());
        stack.setStrength("lora-a", 0.5);
        assertEquals(0.5, stack.all().get(0).strength(), 1e-9);
    }

    @Test void validationRejectsBadStrength() {
        assertThrows(IllegalArgumentException.class, () -> LoRAStack.validate(new LoRAConfig("x", 3.0)));
        assertThrows(IllegalArgumentException.class, () -> new LoRAStack().setStrength("x", -1));
    }

    @Test void metadataParsing() throws Exception {
        String json = "{\"__metadata__\":{\"ss_network_dim\":\"8\",\"ss_network_alpha\":\"8\",\"modelspec.architecture\":\"sd15-lora\"},\"lora_unet_down_blocks_0_attentions_0_transformer_blocks_0_attn1_to_q.lora_down.weight\":{\"dtype\":\"F32\",\"shape\":[8,320],\"data_offsets\":[0,10240]}}";
        SafetensorsHeader h = SafetensorsHeader.parseJson(json, json.length());
        LoRAMetadata m = LoRAMetadata.fromHeader(h);
        assertTrue(m.isValid());
        assertEquals(8, m.networkDim());
        assertTrue(LoRAStack.isCompatible("stable-diffusion-1.5", m));
        assertFalse(LoRAStack.isCompatible("sdxl", m));
    }

    @Test void sdxlCompatibility() throws Exception {
        String json = "{\"__metadata__\":{\"ss_network_dim\":\"4\",\"ss_base_model_version\":\"sdxl-1.0\"},\"lora_te_text_model_encoder_layers_0_self_attn_q_proj.lora_down.weight\":{\"dtype\":\"F16\",\"shape\":[4,1280],\"data_offsets\":[0,10240]}}";
        SafetensorsHeader h = SafetensorsHeader.parseJson(json, json.length());
        LoRAMetadata m = LoRAMetadata.fromHeader(h);
        assertTrue(LoRAStack.isCompatible("sdxl", m));
    }
}
