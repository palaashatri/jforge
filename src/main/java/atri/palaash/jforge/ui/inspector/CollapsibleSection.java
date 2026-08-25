package atri.palaash.jforge.ui.inspector;

import atri.palaash.jforge.ui.animation.Motion;
import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class CollapsibleSection extends JPanel {
    private final JButton headerButton;
    private final JPanel content;
    private boolean expanded = true;

    public CollapsibleSection(String title, JComponent body) {
        super(new BorderLayout(0, 0));
        setBackground(DesignTokens.bgSurface());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesignTokens.borderSeparator(), 1, true),
                BorderFactory.createEmptyBorder(0,0,0,0)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        headerButton = new JButton((expanded ? "▾ " : "▸ ") + title);
        headerButton.setFont(DesignTokens.fontBodyBold());
        headerButton.setForeground(DesignTokens.fgPrimary());
        headerButton.setHorizontalAlignment(SwingConstants.LEFT);
        headerButton.setBorderPainted(false);
        headerButton.setContentAreaFilled(false);
        headerButton.setFocusPainted(false);
        headerButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        headerButton.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        headerButton.setBackground(DesignTokens.bgSurfaceRaised());
        headerButton.setOpaque(true);
        headerButton.addActionListener(e -> setExpanded(!expanded));

        content = new JPanel(new BorderLayout());
        content.setBackground(DesignTokens.bgSurface());
        content.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
        content.add(body, BorderLayout.CENTER);
        content.setVisible(expanded);

        add(headerButton, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    public void setExpanded(boolean e) {
        if (this.expanded == e) return;
        this.expanded = e;
        String t = headerButton.getText().substring(2);
        headerButton.setText((expanded ? "▾ " : "▸ ") + t);
        if (Motion.isReducedMotion()) {
            content.setVisible(expanded);
            revalidate(); repaint();
            Container p = getParent();
            if (p != null) { p.revalidate(); p.repaint(); }
            return;
        }
        if (expanded) {
            content.setVisible(true);
            content.setOpaque(false);
            Motion.animate(Motion.DURATION_MEDIUM, 0f, 1f, alpha -> {
                content.setVisible(true);
                revalidate();
            }, () -> { revalidate(); repaint(); });
        } else {
            Motion.animate(Motion.DURATION_FAST, 1f, 0f, alpha -> {},
                () -> { content.setVisible(false); revalidate(); repaint(); });
        }
        Container p = getParent();
        if (p != null) { p.revalidate(); p.repaint(); }
    }

    public boolean isExpanded() { return expanded; }
}
