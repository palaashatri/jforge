package atri.palaash.jforge.ui.design;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

public final class DesignTokens {
    private DesignTokens() {}

    public static final int SPACE_2 = 2;
    public static final int SPACE_4 = 4;
    public static final int SPACE_8 = 8;
    public static final int SPACE_12 = 12;
    public static final int SPACE_16 = 16;
    public static final int SPACE_20 = 20;
    public static final int SPACE_24 = 24;
    public static final int SPACE_32 = 32;

    public static final int RADIUS_4 = 4;
    public static final int RADIUS_6 = 6;
    public static final int RADIUS_8 = 8;
    public static final int RADIUS_12 = 12;
    public static final int RADIUS_PILL = 999;

    public static final int TOOL_RAIL_WIDTH = 48;
    public static final int INSPECTOR_WIDTH = 300;
    public static final int FILMSTRIP_HEIGHT = 112;
    public static final int STATUS_BAR_HEIGHT = 22;

    public static Color bgCanvas() {
        Color c = UIManager.getColor("Panel.background");
        return c != null ? c : new Color(0x0A0A0B);
    }

    public static Color bgSurface() {
        Color c = UIManager.getColor("Panel.background");
        if (c == null) return new Color(0x121214);
        return isDark() ? new Color(0x121214) : c.brighter();
    }

    public static Color bgSurfaceRaised() {
        return isDark() ? new Color(0x1A1A1E) : new Color(0xFFFFFF);
    }

    public static Color bgSurfaceHover() {
        return isDark() ? new Color(0x252529) : new Color(0xF0F0F3);
    }

    public static Color bgSelected() {
        Color c = UIManager.getColor("List.selectionBackground");
        return c != null ? c : new Color(0x3A6BC5);
    }

    public static Color fgPrimary() {
        Color c = UIManager.getColor("Label.foreground");
        return c != null ? c : new Color(0xE8E8EC);
    }

    public static Color fgMuted() {
        Color c = UIManager.getColor("Label.disabledForeground");
        return c != null ? c : new Color(0x8A8A94);
    }

    public static Color fgSubtle() {
        return isDark() ? new Color(0x6E6E78) : new Color(0x8A8A94);
    }

    public static Color borderSeparator() {
        Color c = UIManager.getColor("Separator.foreground");
        return c != null ? c : (isDark() ? new Color(0x232326) : new Color(0xE2E2E6));
    }

    public static Color accent() { return new Color(0x3A6BC5); }
    public static Color success() { return new Color(0x2E9E6B); }
    public static Color warning() { return new Color(0xD99A2B); }
    public static Color error() { return new Color(0xD94F4F); }

    public static Font fontTiny() { return font(10f, Font.BOLD); }
    public static Font fontCaption() { return font(11f, Font.PLAIN); }
    public static Font fontBody() { return font(12f, Font.PLAIN); }
    public static Font fontBodyBold() { return font(12f, Font.BOLD); }
    public static Font fontLabel() { return font(13f, Font.PLAIN); }

    private static Font font(float size, int style) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return base.deriveFont(style, size);
    }

    public static boolean isDark() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return true;
        double lum = 0.2126 * bg.getRed() + 0.7152 * bg.getGreen() + 0.0722 * bg.getBlue();
        return lum < 128;
    }
}
