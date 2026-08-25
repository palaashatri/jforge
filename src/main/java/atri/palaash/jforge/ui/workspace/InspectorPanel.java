package atri.palaash.jforge.ui.workspace;

import atri.palaash.jforge.ui.design.ComponentStyles;
import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class InspectorPanel extends JPanel {
    private final JPanel contentHost = new JPanel(new BorderLayout());
    private final JLabel titleLabel = new JLabel("Inspector");

    public InspectorPanel() {
        super(new BorderLayout());
        setBackground(DesignTokens.bgSurface());
        setPreferredSize(new Dimension(DesignTokens.INSPECTOR_WIDTH, 0));
        setBorder(ComponentStyles.separatorBorder(false));
        setMinimumSize(new Dimension(240, 0));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DesignTokens.bgSurface());
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 8, 12));
        ComponentStyles.styleSectionHeader(titleLabel);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
        titleLabel.setText("INSPECTOR");
        header.add(titleLabel, BorderLayout.WEST);
        JButton collapse = new JButton("›");
        collapse.setFont(DesignTokens.fontCaption());
        collapse.setBorderPainted(false);
        collapse.setContentAreaFilled(false);
        collapse.setFocusPainted(false);
        collapse.setForeground(DesignTokens.fgMuted());
        header.add(collapse, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        contentHost.setBackground(DesignTokens.bgSurface());
        contentHost.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(contentHost);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(12);
        add(scroll, BorderLayout.CENTER);

        JPanel placeholder = buildPlaceholder();
        setContent(placeholder);
    }

    private JPanel buildPlaceholder() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(DesignTokens.bgSurface());
        p.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        p.add(section("Prompt", "Multiline editor, history, presets. Drag reference images here."));
        p.add(section("Generation", "Steps, CFG, seed, sampler. Sliders with numeric fields."));
        p.add(section("Canvas", "Selection, mask, before/after."));
        return p;
    }

    private JComponent section(String title, String body) {
        JPanel s = new JPanel(new BorderLayout(0, 4));
        s.setBackground(DesignTokens.bgSurfaceRaised());
        s.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesignTokens.borderSeparator(), 1, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        JLabel t = new JLabel(title);
        t.setFont(DesignTokens.fontBodyBold());
        t.setForeground(DesignTokens.fgPrimary());
        JLabel b = new JLabel("<html><body style='width:220px'>" + body + "</body></html>");
        b.setFont(DesignTokens.fontCaption());
        b.setForeground(DesignTokens.fgMuted());
        s.add(t, BorderLayout.NORTH);
        s.add(b, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0,0,8,0));
        wrapper.add(s, BorderLayout.CENTER);
        return wrapper;
    }

    public void setContent(JComponent c) {
        contentHost.removeAll();
        if (c != null) contentHost.add(c, BorderLayout.CENTER);
        revalidate(); repaint();
    }

    public void setInspectorTitle(String title) {
        titleLabel.setText(title.toUpperCase());
    }
}
