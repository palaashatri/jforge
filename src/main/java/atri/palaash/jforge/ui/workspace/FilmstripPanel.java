package atri.palaash.jforge.ui.workspace;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class FilmstripPanel extends JPanel {
    private final JPanel strip = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
    private final JLabel infoLabel = new JLabel("No generations yet");

    public FilmstripPanel() {
        super(new BorderLayout());
        setBackground(DesignTokens.bgSurface());
        setPreferredSize(new Dimension(0, DesignTokens.FILMSTRIP_HEIGHT));
        setBorder(BorderFactory.createMatteBorder(1,0,0,0, DesignTokens.borderSeparator()));
        setMinimumSize(new Dimension(0, 80));

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(DesignTokens.bgSurface());
        header.setBorder(BorderFactory.createEmptyBorder(6, 12, 2, 12));
        JLabel title = new JLabel("FILMSTRIP");
        title.setFont(DesignTokens.fontTiny());
        title.setForeground(DesignTokens.fgMuted());
        header.add(title, BorderLayout.WEST);
        infoLabel.setFont(DesignTokens.fontCaption());
        infoLabel.setForeground(DesignTokens.fgSubtle());
        header.add(infoLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        strip.setBackground(DesignTokens.bgSurface());
        JScrollPane scroll = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getHorizontalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        strip.add(placeholderThumb("Preview"));
    }

    private JComponent placeholderThumb(String label) {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(88, 72));
        p.setBackground(DesignTokens.bgSurfaceRaised());
        p.setBorder(BorderFactory.createLineBorder(DesignTokens.borderSeparator(), 1, true));
        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(DesignTokens.fontCaption());
        l.setForeground(DesignTokens.fgMuted());
        p.add(l, BorderLayout.CENTER);
        return p;
    }

    public void setInfo(String text) { infoLabel.setText(text); }

    public void addThumb(JComponent thumb) {
        if (strip.getComponentCount() == 1 && strip.getComponent(0) instanceof JPanel) {
            strip.removeAll();
        }
        strip.add(thumb);
        revalidate(); repaint();
    }
}
