package atri.palaash.jforge.ui.design;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class DesignTokensTest {
    @Test void spacingScaleIsMonotonic() {
        assertTrue(DesignTokens.SPACE_4 < DesignTokens.SPACE_8);
        assertTrue(DesignTokens.SPACE_8 < DesignTokens.SPACE_12);
        assertTrue(DesignTokens.SPACE_12 < DesignTokens.SPACE_16);
        assertTrue(DesignTokens.SPACE_16 < DesignTokens.SPACE_24);
    }
    @Test void radiiScaleIsMonotonic() {
        assertTrue(DesignTokens.RADIUS_4 < DesignTokens.RADIUS_6);
        assertTrue(DesignTokens.RADIUS_6 < DesignTokens.RADIUS_8);
        assertTrue(DesignTokens.RADIUS_8 < DesignTokens.RADIUS_12);
    }
    @Test void workspaceDimensionsAreSensible() {
        assertTrue(DesignTokens.TOOL_RAIL_WIDTH >= 40 && DesignTokens.TOOL_RAIL_WIDTH <= 64);
        assertTrue(DesignTokens.INSPECTOR_WIDTH >= 240 && DesignTokens.INSPECTOR_WIDTH <= 400);
        assertTrue(DesignTokens.FILMSTRIP_HEIGHT >= 80 && DesignTokens.FILMSTRIP_HEIGHT <= 160);
    }
    @Test void colorsAreNotNull() {
        assertNotNull(DesignTokens.bgCanvas());
        assertNotNull(DesignTokens.bgSurface());
        assertNotNull(DesignTokens.borderSeparator());
        assertNotNull(DesignTokens.accent());
    }
    @Test void fontsAreNotNull() {
        assertNotNull(DesignTokens.fontTiny());
        assertNotNull(DesignTokens.fontCaption());
        assertNotNull(DesignTokens.fontBody());
    }
}
