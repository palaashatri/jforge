package atri.palaash.jforge.ui.inspector;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PromptEditor extends JPanel {
    private final JTextArea area;
    private final JLabel tokenLabel = new JLabel("0 tokens");
    private final JComboBox<String> historyBox = new JComboBox<>();
    private final List<String> history = new ArrayList<>();

    public PromptEditor(String placeholder) {
        super(new BorderLayout(0, 4));
        setBackground(DesignTokens.bgSurface());
        area = new JTextArea(4, 20);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(DesignTokens.fontBody());
        area.putClientProperty("JTextField.placeholderText", placeholder);
        area.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(DesignTokens.borderSeparator(), 1, true));
        scroll.setPreferredSize(new Dimension(0, 72));
        add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(0,0));
        footer.setOpaque(false);
        tokenLabel.setFont(DesignTokens.fontCaption());
        tokenLabel.setForeground(DesignTokens.fgSubtle());
        footer.add(tokenLabel, BorderLayout.WEST);
        historyBox.setFont(DesignTokens.fontCaption());
        historyBox.setToolTipText("Prompt history");
        historyBox.addActionListener(e -> {
            String sel = (String) historyBox.getSelectedItem();
            if (sel != null && !sel.isBlank()) area.setText(sel);
        });
        JPanel histWrap = new JPanel(new BorderLayout(4,0));
        histWrap.setOpaque(false);
        JLabel h = new JLabel("↻");
        h.setFont(DesignTokens.fontCaption());
        h.setForeground(DesignTokens.fgMuted());
        histWrap.add(h, BorderLayout.WEST);
        histWrap.add(historyBox, BorderLayout.CENTER);
        histWrap.setPreferredSize(new Dimension(120, 22));
        footer.add(histWrap, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        area.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateTokenCount(); }
            public void removeUpdate(DocumentEvent e) { updateTokenCount(); }
            public void changedUpdate(DocumentEvent e) { updateTokenCount(); }
        });
    }

    private void updateTokenCount() {
        String t = area.getText().trim();
        int tokens = t.isEmpty() ? 0 : t.split("\\s+").length;
        tokenLabel.setText(tokens + " tokens");
        if (tokens > 75) tokenLabel.setForeground(DesignTokens.warning());
        else tokenLabel.setForeground(DesignTokens.fgSubtle());
    }

    public String getPrompt() { return area.getText(); }
    public void setPrompt(String s) { area.setText(s); }
    public JTextArea getTextArea() { return area; }
    public void pushHistory(String prompt) {
        if (prompt == null || prompt.isBlank() || history.contains(prompt)) return;
        history.add(0, prompt);
        if (history.size() > 20) history.remove(history.size()-1);
        historyBox.removeAllItems();
        for (String s : history) historyBox.addItem(s.length() > 40 ? s.substring(0,40)+"…" : s);
    }
}
