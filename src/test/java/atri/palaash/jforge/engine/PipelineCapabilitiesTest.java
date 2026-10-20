package atri.palaash.jforge.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineCapabilitiesTest {

    @Test
    void builderMarksSupportedCapabilities() {
        PipelineCapabilities caps = PipelineCapabilities.builder()
                .capabilities(Capability.TEXT_TO_IMAGE, Capability.CFG,
                        Capability.NEGATIVE_PROMPT, Capability.LORA)
                .build();

        assertTrue(caps.has(Capability.TEXT_TO_IMAGE));
        assertTrue(caps.has(Capability.CFG));
        assertTrue(caps.supportsCfg());
        assertTrue(caps.supportsNegativePrompt());
        assertTrue(caps.has(Capability.LORA));
        assertFalse(caps.has(Capability.CONTROLNET));
        assertFalse(caps.has(Capability.VIDEO));
        assertFalse(caps.has(Capability.INPAINT));
    }

    @Test
    void recommendedSchedulerDefaultsFromSupported() {
        PipelineCapabilities caps = PipelineCapabilities.builder()
                .capabilities(Capability.TEXT_TO_IMAGE)
                .scheduler(atri.palaash.jforge.api.SchedulerType.EULER)
                .build();
        assertEquals(atri.palaash.jforge.api.SchedulerType.EULER, caps.recommendedScheduler());
    }

    @Test
    void defaultCapabilitiesAreFalse() {
        PipelineCapabilities caps = PipelineCapabilities.builder().build();
        assertFalse(caps.has(Capability.TEXT_TO_IMAGE));
        assertFalse(caps.supportsCfg());
        assertEquals(2048, caps.maxResolution());
        assertTrue(caps.supportsScheduler(atri.palaash.jforge.api.SchedulerType.DDIM));
    }
}