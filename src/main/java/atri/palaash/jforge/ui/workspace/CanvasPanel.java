package atri.palaash.jforge.ui.workspace;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class CanvasPanel extends JPanel {
    private double zoom = 1.0;
    private Point pan = new Point(0, 0);
    private Point dragStart;

    public CanvasPanel() {
        setBackground(DesignTokens.bgCanvas());
        setLayout(new BorderLayout());
        JLabel hint = new JLabel("Canvas  ·  drag to pan  ·  scroll to zoom  ·  100% → click Fit", SwingConstants.CENTER);
        hint.setFont(DesignTokens.fontCaption());
        hint.setForeground(DesignTokens.fgSubtle());
        hint.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
        add(hint, BorderLayout.SOUTH);

        MouseAdapter handler = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { dragStart = e.getPoint(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (dragStart != null) {
                    pan.translate(e.getX() - dragStart.x, e.getY() - dragStart.y);
                    dragStart = e.getPoint();
                    repaint();
                }
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getPreciseWheelRotation() < 0 ? 1.1 : 0.9;
                zoom = Math.max(0.1, Math.min(8.0, zoom * factor));
                repaint();
            }
        };
        addMouseListener(handler);
        addMouseMotionListener(handler);
        addMouseWheelListener(handler);
    }

    public double getZoom() { return zoom; }
    public void setZoom(double z) { this.zoom = Math.max(0.1, Math.min(8.0, z)); repaint(); }
    public void fitToView() { zoom = 1.0; pan.setLocation(0,0); repaint(); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        int checker = 12;
        Color c1 = DesignTokens.isDark() ? new Color(0x18181B) : new Color(0xEDEDF0);
        Color c2 = DesignTokens.isDark() ? new Color(0x1E1E22) : new Color(0xF5F5F7);
        for (int y = 0; y < h; y += checker) {
            for (int x = 0; x < w; x += checker) {
                g2.setColor(((x/checker + y/checker) % 2 == 0) ? c1 : c2);
                g2.fillRect(x, y, checker, checker);
            }
        }
        int cw = (int)(512 * zoom), ch = (int)(512 * zoom);
        int cx = w/2 - cw/2 + pan.x, cy = h/2 - ch/2 + pan.y;
        g2.setColor(DesignTokens.bgSurfaceRaised());
        g2.fillRoundRect(cx, cy, cw, ch, 8, 8);
        g2.setColor(DesignTokens.borderSeparator());
        g2.drawRoundRect(cx, cy, cw, ch, 8, 8);
        g2.setColor(DesignTokens.fgSubtle());
        g2.setFont(DesignTokens.fontCaption());
        String label = String.format("%.0f%%", zoom * 100);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(label, cx + (cw - fm.stringWidth(label))/2, cy + ch/2 + fm.getAscent()/2);
        g2.dispose();
    }
}
