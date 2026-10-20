package atri.palaash.jforge.ui.console;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DeveloperConsole extends JPanel {
    private final JTextArea logArea = new JTextArea();
    private final JLabel backendLabel = new JLabel("Backend: —");
    private final JLabel memoryLabel = new JLabel("Memory: —");
    private final JLabel modelLabel = new JLabel("Model: —");

    public DeveloperConsole() {
        super(new BorderLayout(0, 0));
        setBackground(DesignTokens.bgSurface());

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 6));
        header.setBackground(DesignTokens.bgSurface());
        header.setBorder(BorderFactory.createMatteBorder(0,0,1,0, DesignTokens.borderSeparator()));
        for (JLabel l : new JLabel[]{backendLabel, memoryLabel, modelLabel}) {
            l.setFont(DesignTokens.fontCaption());
            l.setForeground(DesignTokens.fgMuted());
            header.add(l);
        }
        JButton clear = new JButton("Clear");
        clear.setFont(DesignTokens.fontCaption());
        clear.addActionListener(e -> logArea.setText(""));
        header.add(clear);
        add(header, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(DesignTokens.bgCanvas());
        logArea.setForeground(DesignTokens.fgPrimary());
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
    }

    public void log(String level, String message) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append(String.format("[%s] %s: %s%n", ts, level, message));
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void setBackendInfo(String backend, String device) {
        backendLabel.setText("Backend: " + backend + " · " + device);
    }

    public void setMemoryInfo(String text) { memoryLabel.setText("Memory: " + text); }
    public void setModelInfo(String text) { modelLabel.setText("Model: " + text); }
}
