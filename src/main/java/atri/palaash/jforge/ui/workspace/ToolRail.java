package atri.palaash.jforge.ui.workspace;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class ToolRail extends JPanel {
    public enum Tool { SELECT, BRUSH, TEXT, SHAPE, HAND }
    public interface ToolListener { void onToolSelected(Tool tool); }

    private Tool selected = Tool.SELECT;
    private final ToolListener listener;

    public ToolRail(ToolListener listener) {
        super();
        this.listener = listener;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(DesignTokens.bgSurface());
        setPreferredSize(new Dimension(DesignTokens.TOOL_RAIL_WIDTH, 0));
        setBorder(BorderFactory.createMatteBorder(0,0,0,1, DesignTokens.borderSeparator()));
        add(Box.createVerticalStrut(8));
        addButton("◧", "Select / Move (V)", Tool.SELECT);
        addButton("✎", "Brush / Mask (B)", Tool.BRUSH);
        addButton("T", "Text (T)", Tool.TEXT);
        addButton("◇", "Shape (U)", Tool.SHAPE);
        addButton("✋", "Pan / Hand (H)", Tool.HAND);
        add(Box.createVerticalGlue());
        addButton("⚙", "Settings", null);
    }

    private void addButton(String glyph, String tooltip, Tool tool) {
        JButton b = new JButton(glyph);
        b.setToolTipText(tooltip);
        b.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(true);
        b.setBackground(tool == selected ? DesignTokens.bgSelected() : DesignTokens.bgSurface());
        b.setForeground(tool == selected ? Color.WHITE : DesignTokens.fgMuted());
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(32, 32));
        b.setPreferredSize(new Dimension(32, 32));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> {
            if (tool != null) {
                selected = tool;
                refreshSelection();
                if (listener != null) listener.onToolSelected(tool);
            }
        });
        add(b);
        add(Box.createVerticalStrut(4));
    }

    private void refreshSelection() {
        for (Component c : getComponents()) {
            if (c instanceof JButton b) {
                String tip = b.getToolTipText();
                boolean isSel = tip != null && tip.contains(selected.name());
                b.setBackground(isSel ? DesignTokens.bgSelected() : DesignTokens.bgSurface());
                b.setForeground(isSel ? Color.WHITE : DesignTokens.fgMuted());
            }
        }
        repaint();
    }

    public Tool getSelectedTool() { return selected; }
}
