package atri.palaash.jforge.ui.workspace;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class GenerationStatusBar extends JPanel {
    private final JLabel progressLabel = new JLabel("Ready");
    private final JLabel deviceLabel = new JLabel("");
    private final JLabel memoryLabel = new JLabel("");
    private final JProgressBar bar = new JProgressBar();
    private final JButton cancelButton = new JButton("Cancel");

    public GenerationStatusBar() {
        super(new BorderLayout(8, 0));
        setBackground(DesignTokens.bgSurface());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1,0,0,0, DesignTokens.borderSeparator()),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));
        setPreferredSize(new Dimension(0, 28));

        progressLabel.setFont(DesignTokens.fontCaption());
        progressLabel.setForeground(DesignTokens.fgPrimary());

        deviceLabel.setFont(DesignTokens.fontCaption());
        deviceLabel.setForeground(DesignTokens.fgMuted());

        memoryLabel.setFont(DesignTokens.fontCaption());
        memoryLabel.setForeground(DesignTokens.fgMuted());

        bar.setPreferredSize(new Dimension(140, 6));
        bar.setMaximumSize(new Dimension(140, 6));
        bar.setVisible(false);

        cancelButton.setFont(DesignTokens.fontCaption());
        cancelButton.setVisible(false);
        cancelButton.setFocusPainted(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(progressLabel);
        left.add(bar);
        left.add(cancelButton);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(deviceLabel);
        right.add(memoryLabel);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void setProgress(String text, int step, int total, double itPerSec, String eta) {
        if (total > 0) {
            bar.setVisible(true);
            bar.setMaximum(total);
            bar.setValue(step);
            bar.setIndeterminate(false);
            progressLabel.setText(String.format("%s  %d/%d  %.1f it/s  ETA %s", text, step, total, itPerSec, eta));
        } else {
            bar.setVisible(true);
            bar.setIndeterminate(true);
            progressLabel.setText(text);
        }
    }

    public void setDeviceInfo(String device, String backend, String memory) {
        deviceLabel.setText(device + " · " + backend);
        memoryLabel.setText(memory);
    }

    public void setCancelAction(Runnable onCancel) {
        cancelButton.setVisible(onCancel != null);
        for (java.awt.event.ActionListener al : cancelButton.getActionListeners()) cancelButton.removeActionListener(al);
        if (onCancel != null) cancelButton.addActionListener(e -> onCancel.run());
    }

    public void setIdle(String text) {
        progressLabel.setText(text);
        bar.setVisible(false);
        cancelButton.setVisible(false);
    }
}
