package atri.palaash.jforge.ui.inspector;

import atri.palaash.jforge.engine.Capability;
import atri.palaash.jforge.engine.PipelineCapabilities;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InspectorControllerTest {
    @Test void promptControlsAlwaysVisible() {
        InspectorController c = new InspectorController();
        assertNotNull(c.promptEditor());
        assertNotNull(c.negativeEditor());
        assertNotNull(c.stepsField());
        assertNotNull(c.getView());
    }

    @Test void cfgHiddenWhenCapabilityMissing() {
        InspectorController c = new InspectorController();
        PipelineCapabilities noCfg = PipelineCapabilities.builder()
                .capabilities(Capability.TEXT_TO_IMAGE)
                .build();
        c.bindCapabilities(noCfg);
        assertFalse(c.cfgField().isVisible(), "CFG should be hidden when pipeline lacks CFG capability");
        assertTrue(c.stepsField().isVisible());
    }

    @Test void cfgVisibleWhenCapabilityPresent() {
        InspectorController c = new InspectorController();
        PipelineCapabilities withCfg = PipelineCapabilities.builder()
                .capabilities(Capability.TEXT_TO_IMAGE, Capability.CFG, Capability.NEGATIVE_PROMPT)
                .build();
        c.bindCapabilities(withCfg);
        assertTrue(c.cfgField().isVisible());
        assertTrue(c.negativeEditor().isVisible());
    }

    @Test void promptEditorTracksHistory() {
        InspectorController c = new InspectorController();
        PromptEditor e = c.promptEditor();
        e.setPrompt("a cat");
        assertEquals("a cat", e.getPrompt());
        e.pushHistory("a cat");
        e.pushHistory("a dog");
        assertDoesNotThrow(() -> e.pushHistory("a cat"));
    }

    @Test void sliderFieldSyncs() {
        SliderField f = new SliderField("Test", 0, 100, 50);
        f.setValue(75);
        assertEquals(75, f.getValue());
        assertEquals(75, f.getSlider().getValue());
        assertEquals(75, (Integer) f.getSpinner().getValue());
    }
}
