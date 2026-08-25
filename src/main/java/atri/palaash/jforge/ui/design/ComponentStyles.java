package atri.palaash.jforge.ui.design;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public final class ComponentStyles {
    private ComponentStyles() {}

    public static Border separatorBorder(boolean top) {
        return new Border() {
            @Override public Insets getBorderInsets(Component c) {
                return top ? new Insets(1,0,0,0) : new Insets(0,0,0,1);
            }
            @Override public boolean isBorderOpaque() { return true; }
            @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
                g.setColor(DesignTokens.borderSeparator());
                if (top) g.drawLine(x, y, x+w, y);
                else g.drawLine(x+w-1, y, x+w-1, y+h);
            }
        };
    }

    public static void styleSectionHeader(JLabel label) {
        label.setFont(DesignTokens.fontTiny());
        label.setForeground(DesignTokens.fgMuted());
        label.setBorder(BorderFactory.createEmptyBorder(6, 12, 4, 12));
    }

    public static void styleInspectorPanel(JPanel p) {
        p.setBackground(DesignTokens.bgSurface());
        p.setBorder(separatorBorder(false));
    }

    public static JButton createToolButton(String glyph, String tooltip) {
        JButton b = new JButton(glyph);
        b.setToolTipText(tooltip);
        b.setFont(DesignTokens.fontLabel());
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setPreferredSize(new Dimension(36, 36));
        b.setMaximumSize(new Dimension(36, 36));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
