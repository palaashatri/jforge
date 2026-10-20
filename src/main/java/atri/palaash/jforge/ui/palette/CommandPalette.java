package atri.palaash.jforge.ui.palette;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.function.Consumer;

public class CommandPalette extends JDialog {
    public record Command(String id, String label, String description, Runnable action) {}

    private final JTextField input = new JTextField();
    private final DefaultListModel<Command> model = new DefaultListModel<>();
    private final JList<Command> list = new JList<>(model);
    private final List<Command> allCommands;

    public CommandPalette(Window owner, List<Command> commands, Consumer<String> onExecute) {
        super(owner, "Command Palette", ModalityType.APPLICATION_MODAL);
        this.allCommands = List.copyOf(commands);
        setUndecorated(true);
        setSize(520, 340);
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(DesignTokens.bgSurfaceRaised());
        root.setBorder(BorderFactory.createLineBorder(DesignTokens.borderSeparator(), 1, true));

        input.setFont(DesignTokens.fontBody());
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,0,1,0, DesignTokens.borderSeparator()),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        input.addActionListener(e -> executeSelected(onExecute));
        input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(); }
        });
        root.add(input, BorderLayout.NORTH);

        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int idx, boolean sel, boolean foc) {
                super.getListCellRendererComponent(l, v, idx, sel, foc);
                if (v instanceof Command c) {
                    setText(c.label() + "  —  " + c.description());
                    setFont(DesignTokens.fontBody());
                    setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
                    if (sel) { setBackground(DesignTokens.bgSelected()); setForeground(Color.WHITE); }
                    else { setBackground(DesignTokens.bgSurfaceRaised()); setForeground(DesignTokens.fgPrimary()); }
                }
                return this;
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) executeSelected(onExecute);
            }
        });
        list.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) executeSelected(onExecute);
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) dispose();
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        JLabel hint = new JLabel("  ↑↓ to navigate  ·  Enter to execute  ·  Esc to close  ·  Type to filter");
        hint.setFont(DesignTokens.fontCaption());
        hint.setForeground(DesignTokens.fgMuted());
        hint.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        root.add(hint, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        filter();
    }

    private void filter() {
        String q = input.getText().trim().toLowerCase();
        model.clear();
        for (Command c : allCommands) {
            if (q.isEmpty() || c.label().toLowerCase().contains(q) || c.description().toLowerCase().contains(q) || c.id().toLowerCase().contains(q)) {
                model.addElement(c);
            }
        }
        if (!model.isEmpty()) list.setSelectedIndex(0);
    }

    private void executeSelected(Consumer<String> onExecute) {
        Command sel = list.getSelectedValue();
        if (sel != null) {
            dispose();
            if (onExecute != null) onExecute.accept(sel.id());
            sel.action().run();
        }
    }

    public static void registerShortcut(JRootPane root, Runnable show) {
        int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_K, mask), "palette");
        root.getActionMap().put("palette", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { show.run(); }
        });
    }
}
