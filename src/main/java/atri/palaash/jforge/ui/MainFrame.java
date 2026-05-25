package atri.palaash.jforge.ui;

import atri.palaash.jforge.inference.InferenceService;
import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.ModelRegistry;
import atri.palaash.jforge.model.TaskType;
import atri.palaash.jforge.storage.ModelDownloader;
import atri.palaash.jforge.storage.ModelStorage;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.ListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Main application window — Apple Human Interface Guidelines–inspired layout.
 * <ul>
 *   <li>Left sidebar with grouped navigation (Imagine / Enhance) and Models at the bottom</li>
 *   <li>Card-layout content area</li>
 *   <li>Menu bar: View, Inference, Tools</li>
 * </ul>
 */
public class MainFrame extends JFrame {

    /* Card names */
    private static final String CARD_GENERATE = "Imagine";
    private static final String CARD_UPSCALE  = "Enhance";
    private static final String CARD_MODELS   = "Models";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);

    /* Lazy-created panels */
    private TextToImagePanel textToImagePanel;
    private ImageUpscalePanel imageUpscalePanel;
    private ModelManagerPanel modelManagerPanel;

    /* Factory state for lazy construction */
    private final ModelRegistry registry;
    private final ModelStorage storage;
    private final ModelDownloader downloader;
    private final Map<TaskType, InferenceService> services;
    private final JLabel statusBarLabel;

    /* Sidebar lists */
    private JList<String> workflowList;
    private JList<String> managementList;

    /* GPU state (shared across panels via supplier) */
    private boolean gpuEnabled = true;

    /* Shared Runnable for navigating to Models panel */
    private final Runnable goToModels;

    /**
     * Constructs the main application window with a sidebar, card-based content
     * panel, menu bar, and status bar. Only the Text-to-Image panel is created
     * eagerly; other panels are lazily constructed on first navigation.
     *
     * @param registry  the model registry containing all known model descriptors
     * @param storage   the model storage backend for checking local availability
     * @param downloader handles downloading models from remote sources
     * @param services   map of task types to their inference services
     */
    public MainFrame(ModelRegistry registry,
                     ModelStorage storage,
                     ModelDownloader downloader,
                     Map<TaskType, InferenceService> services) {
        this.registry = registry;
        this.storage = storage;
        this.downloader = downloader;
        this.services = services;

        setTitle("JForge");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1060, 740);
        setMinimumSize(new Dimension(800, 540));
        setLocationRelativeTo(null);

        this.goToModels = () -> {
            if (managementList != null) switchToCard(CARD_MODELS, managementList, 0);
        };

        /* ── Create only the initially-visible panel ───────────── */
        textToImagePanel = createTextToImagePanel();

        /* ── Content cards (lazy: only TextToImage now) ───────── */
        contentPanel.add(textToImagePanel, CARD_GENERATE);
        contentPanel.add(new JPanel(), CARD_UPSCALE);
        contentPanel.add(new JPanel(), CARD_MODELS);

        /* ── Sidebar ─────────────────────────────────────────────── */
        String[] workflowItems = {CARD_GENERATE, CARD_UPSCALE};
        workflowList = new JList<>(workflowItems);
        workflowList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        workflowList.setFixedCellHeight(32);
        workflowList.setCellRenderer(new SidebarRenderer());
        workflowList.setOpaque(false);

        String[] managementItems = {CARD_MODELS};
        managementList = new JList<>(managementItems);
        managementList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        managementList.setFixedCellHeight(32);
        managementList.setCellRenderer(new SidebarRenderer());
        managementList.setOpaque(false);

        workflowList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && workflowList.getSelectedIndex() >= 0) {
                managementList.clearSelection();
                switchToCard(workflowList.getSelectedValue(), workflowList, workflowList.getSelectedIndex());
            }
        });
        managementList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && managementList.getSelectedIndex() >= 0) {
                workflowList.clearSelection();
                switchToCard(managementList.getSelectedValue(), managementList, managementList.getSelectedIndex());
            }
        });

        workflowList.setSelectedIndex(0);

        JPanel sidebarPanel = new JPanel(new BorderLayout(0, 0));
        sidebarPanel.setPreferredSize(new Dimension(170, 0));
        sidebarPanel.setBorder(new SeparatorBorder());

        JLabel brand = new JLabel("JForge");
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 13f));
        brand.setBorder(BorderFactory.createEmptyBorder(14, 20, 12, 20));
        sidebarPanel.add(brand, BorderLayout.NORTH);

        JPanel workflowSection = new JPanel();
        workflowSection.setLayout(new BoxLayout(workflowSection, BoxLayout.Y_AXIS));
        workflowSection.setOpaque(false);
        workflowSection.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JLabel workflowHeader = new JLabel("WORKSPACE");
        workflowHeader.setFont(workflowHeader.getFont().deriveFont(Font.BOLD, 10f));
        workflowHeader.setForeground(UIManager.getColor("Label.disabledForeground"));
        workflowHeader.setBorder(BorderFactory.createEmptyBorder(4, 20, 4, 20));
        workflowHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        workflowSection.add(workflowHeader);
        workflowList.setAlignmentX(Component.LEFT_ALIGNMENT);
        workflowSection.add(workflowList);

        JPanel managementSection = new JPanel();
        managementSection.setLayout(new BoxLayout(managementSection, BoxLayout.Y_AXIS));
        managementSection.setOpaque(false);

        JLabel managementHeader = new JLabel("MANAGEMENT");
        managementHeader.setFont(managementHeader.getFont().deriveFont(Font.BOLD, 10f));
        managementHeader.setForeground(UIManager.getColor("Label.disabledForeground"));
        managementHeader.setBorder(BorderFactory.createEmptyBorder(8, 20, 4, 20));
        managementHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        managementSection.add(managementHeader);
        managementList.setAlignmentX(Component.LEFT_ALIGNMENT);
        managementSection.add(managementList);

        JPanel bottomSection = new JPanel();
        bottomSection.setLayout(new BoxLayout(bottomSection, BoxLayout.Y_AXIS));
        bottomSection.setOpaque(false);
        bottomSection.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        bottomSection.add(managementSection);

        JPanel sidebarContent = new JPanel(new BorderLayout());
        sidebarContent.setOpaque(false);
        sidebarContent.add(workflowSection, BorderLayout.NORTH);
        sidebarContent.add(bottomSection, BorderLayout.SOUTH);

        sidebarPanel.add(sidebarContent, BorderLayout.CENTER);

        /* ── Status bar ──────────────────────────────────────────── */
        statusBarLabel = new JLabel(detectEpInfo());
        statusBarLabel.setFont(statusBarLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statusBarLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        statusBarLabel.setBorder(BorderFactory.createCompoundBorder(
                new SeparatorBorder(true),
                BorderFactory.createEmptyBorder(4, 16, 4, 16)));

        /* ── Root assembly ───────────────────────────────────────── */
        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebarPanel, BorderLayout.WEST);
        root.add(contentPanel, BorderLayout.CENTER);
        root.add(statusBarLabel, BorderLayout.SOUTH);
        setContentPane(root);

        /* ── Menu bar ────────────────────────────────────────────── */
        setJMenuBar(buildMenuBar());
    }

    /**
     * Creates and configures the Text-to-Image panel, wiring it to model storage,
     * the model manager navigation callback, and the GPU supplier.
     *
     * @return a fully wired TextToImagePanel
     */
    private TextToImagePanel createTextToImagePanel() {
        List<ModelDescriptor> t2iModels = registry.allModels().stream()
                .filter(m -> m.taskType() == TaskType.TEXT_TO_IMAGE)
                .collect(Collectors.toList());
        TextToImagePanel panel = new TextToImagePanel(
                t2iModels, downloader, services.get(TaskType.TEXT_TO_IMAGE));
        panel.setModelStorage(storage);
        panel.setOpenModelManager(goToModels);
        panel.setGpuSupplier(() -> gpuEnabled);
        panel.updateModels(t2iModels);
        return panel;
    }

    /**
     * Lazily creates and configures the Image Upscale panel, wiring it to
     * model storage, the model manager navigation callback, and the GPU supplier.
     *
     * @return a fully wired ImageUpscalePanel
     */
    private ImageUpscalePanel createImageUpscalePanel() {
        List<ModelDescriptor> upscaleModels = registry.allModels().stream()
                .filter(m -> m.taskType() == TaskType.IMAGE_UPSCALE)
                .collect(Collectors.toList());
        ImageUpscalePanel panel = new ImageUpscalePanel(
                upscaleModels, downloader, services.get(TaskType.IMAGE_UPSCALE));
        panel.setModelStorage(storage);
        panel.setOpenModelManager(goToModels);
        panel.setGpuSupplier(() -> gpuEnabled);
        panel.updateModels(upscaleModels);
        return panel;
    }

    /**
     * Lazily creates and configures the Model Manager panel, registering a
     * callback that refreshes the model lists in the other panels when models
     * are added or removed.
     *
     * @return a fully wired ModelManagerPanel
     */
    private ModelManagerPanel createModelManagerPanel() {
        ModelManagerPanel panel = new ModelManagerPanel(registry, storage, downloader);
        panel.setOnModelsUpdated(() -> {
            if (textToImagePanel != null) {
                textToImagePanel.updateModels(
                        registry.allModels().stream()
                                .filter(m -> m.taskType() == TaskType.TEXT_TO_IMAGE)
                                .collect(Collectors.toList()));
            }
            if (imageUpscalePanel != null) {
                imageUpscalePanel.updateModels(
                        registry.allModels().stream()
                                .filter(m -> m.taskType() == TaskType.IMAGE_UPSCALE)
                                .collect(Collectors.toList()));
            }
        });
        return panel;
    }

    /**
     * Ensure a lazily-created panel exists and is installed in the card layout.
     */
    private Object getOrCreatePanel(String card) {
        return switch (card) {
            case CARD_GENERATE -> textToImagePanel;
            case CARD_UPSCALE -> {
                if (imageUpscalePanel == null) {
                    imageUpscalePanel = createImageUpscalePanel();
                    contentPanel.add(imageUpscalePanel, CARD_UPSCALE);
                }
                yield imageUpscalePanel;
            }
            case CARD_MODELS -> {
                if (modelManagerPanel == null) {
                    modelManagerPanel = createModelManagerPanel();
                    contentPanel.add(modelManagerPanel, CARD_MODELS);
                }
                yield modelManagerPanel;
            }
            default -> throw new IllegalArgumentException("Unknown card: " + card);
        };
    }

    /* ================================================================== */
    /*  Menu bar                                                           */
    /* ================================================================== */

    /**
     * Builds the menu bar with View (dark mode, navigation), Inference (GPU toggle),
     * and Tools (save preset, open logs) menus.
     *
     * @return the configured JMenuBar
     */
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

        /* View */
        JMenu viewMenu = new JMenu("View");

        JCheckBoxMenuItem darkModeItem = new JCheckBoxMenuItem("Dark Mode");
        darkModeItem.setSelected(NativeLookAndFeel.isDarkMode());
        darkModeItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, menuMask | KeyEvent.SHIFT_DOWN_MASK));
        darkModeItem.addActionListener(e -> {
            NativeLookAndFeel.setDarkMode(darkModeItem.isSelected());
            repaintAll();
        });
        viewMenu.add(darkModeItem);

        viewMenu.addSeparator();

        JMenuItem showImagine = new JMenuItem("Imagine");
        showImagine.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_1, menuMask));
        showImagine.addActionListener(e -> switchToCard(CARD_GENERATE, workflowList, 0));
        viewMenu.add(showImagine);

        JMenuItem showEnhance = new JMenuItem("Enhance");
        showEnhance.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_2, menuMask));
        showEnhance.addActionListener(e -> switchToCard(CARD_UPSCALE, workflowList, 1));
        viewMenu.add(showEnhance);

        viewMenu.addSeparator();

        JMenuItem showModels = new JMenuItem("Models");
        showModels.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_M, menuMask));
        showModels.addActionListener(e -> switchToCard(CARD_MODELS, managementList, 0));
        viewMenu.add(showModels);

        bar.add(viewMenu);

        /* Inference */
        JMenu inferenceMenu = new JMenu("Inference");
        JCheckBoxMenuItem gpuItem = new JCheckBoxMenuItem("Use GPU");
        gpuItem.setSelected(gpuEnabled);
        gpuItem.addActionListener(e -> {
            gpuEnabled = gpuItem.isSelected();
            statusBarLabel.setText(detectEpInfo());
        });
        inferenceMenu.add(gpuItem);
        bar.add(inferenceMenu);

        /* Tools */
        JMenu toolsMenu = new JMenu("Tools");
        JMenuItem savePreset = new JMenuItem("Save Current Preset");
        savePreset.addActionListener(e -> textToImagePanel.saveCurrentPreset());
        JMenuItem openLogs = new JMenuItem("Open Logs Folder");
        openLogs.addActionListener(e -> textToImagePanel.openLogsFolder());
        toolsMenu.add(savePreset);
        toolsMenu.add(openLogs);
        bar.add(toolsMenu);

        return bar;
    }

    /* ================================================================== */
    /*  Navigation helpers                                                 */
    /* ================================================================== */

    /**
     * Switches the content panel to the named card, ensuring the panel is
     * lazily created if needed, and updates sidebar selection.
     *
     * @param card       the card name (e.g. "Imagine", "Enhance", "Models")
     * @param targetList the sidebar list to mark as selected
     * @param index      the index to select in the target list
     */
    private void switchToCard(String card, JList<String> targetList, int index) {
        getOrCreatePanel(card);
        cardLayout.show(contentPanel, card);
        if (targetList != workflowList) workflowList.clearSelection();
        if (targetList != managementList) managementList.clearSelection();
        targetList.setSelectedIndex(index);
    }

    /* ================================================================== */
    /*  L&F helpers                                                        */
    /* ================================================================== */

    /**
     * Refreshes the UI of all open windows after a look-and-feel change
     * (e.g. toggling dark mode).
     */
    private void repaintAll() {
        for (java.awt.Window w : java.awt.Window.getWindows()) {
            javax.swing.SwingUtilities.updateComponentTreeUI(w);
        }
    }

    /* ================================================================== */
    /*  Sidebar renderer — HIG-inspired with rounded selection             */
    /* ================================================================== */

    /**
     * Custom list cell renderer for the sidebar. Draws a rounded-rectangle
     * selection background to match Apple HIG styling.
     */
    private static class SidebarRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel label = new JLabel();
        private boolean selected;

        /**
         * Constructs the renderer panel with a border layout and empty insets.
         */
        SidebarRenderer() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
            label.setOpaque(false);
            label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 20));
            add(label, BorderLayout.CENTER);
        }

        /**
         * Configures the renderer component for each list cell, setting text and
         * foreground color based on selection state.
         *
         * @param list         the JList being rendered
         * @param value        the cell value (sidebar item name)
         * @param index        the cell index
         * @param isSelected   whether the cell is selected
         * @param cellHasFocus whether the cell has keyboard focus
         * @return this component configured for the cell
         */
        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                                                       String value, int index,
                                                       boolean isSelected, boolean cellHasFocus) {
            this.selected = isSelected;
            label.setText(value);
            label.setFont(list.getFont().deriveFont(Font.PLAIN, 13f));
            if (isSelected) {
                label.setForeground(UIManager.getColor("List.selectionForeground"));
            } else {
                label.setForeground(list.getForeground());
            }
            return this;
        }

        /**
         * Paints a rounded-rectangle selection background when the item is selected,
         * before painting the normal component background.
         *
         * @param g the graphics context
         */
        @Override
        protected void paintComponent(Graphics g) {
            if (selected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = UIManager.getColor("List.selectionBackground");
                if (bg == null) bg = new Color(56, 117, 215);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    /* ================================================================== */
    /*  Status bar EP detection                                            */
    /* ================================================================== */

    /**
     * Detects the available execution provider (GPU/CPU) and builds a status
     * bar string showing the provider, CPU core count, heap size, and Java version.
     *
     * @return a formatted status string like "EP: CUDA | Cores: 8 | Heap: 4096 MB | Java 21"
     */
    private String detectEpInfo() {
        String detected = atri.palaash.jforge.inference.GenericOnnxService.detectedProvider();
        String ep = detected.isBlank() ? (gpuEnabled ? "GPU unavailable, CPU" : "CPU") : detected;
        int cores = Runtime.getRuntime().availableProcessors();
        long mem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        return "EP: " + ep + "  \u2502  Cores: " + cores + "  \u2502  Heap: " + mem
                + " MB  \u2502  Java " + Runtime.version();
    }

    /* ================================================================== */
    /*  Separator border (top or right edge)                               */
    /* ================================================================== */

    /**
     * A thin one-pixel border used as a visual separator between layout sections.
     * Draws a line on the top or right edge depending on configuration.
     */
    private static class SeparatorBorder implements Border {
        private final boolean top;
        /**
         * Creates a right-edge separator (the default sidebar divider).
         */
        SeparatorBorder() { this(false); }

        /**
         * Creates a separator on the specified edge.
         *
         * @param top true for a top-edge separator, false for a right-edge separator
         */
        SeparatorBorder(boolean top) { this.top = top; }
        /**
         * Returns the insets: 1 pixel on the top or right edge, 0 elsewhere.
         *
         * @param c the component (unused)
         * @return the border insets
         */
        @Override public Insets getBorderInsets(Component c) {
            return top ? new Insets(1, 0, 0, 0) : new Insets(0, 0, 0, 1);
        }
        /**
         * Returns true since the border paints every pixel within its insets.
         *
         * @return true
         */
        @Override public boolean isBorderOpaque() { return true; }
        /**
         * Paints a single-pixel line on the configured edge.
         *
         * @param c      the component
         * @param g      the graphics context
         * @param x      the x origin
         * @param y      the y origin
         * @param width  the component width
         * @param height the component height
         */
        @Override public void paintBorder(Component c, Graphics g,
                                            int x, int y, int width, int height) {
            g.setColor(UIManager.getColor("Separator.foreground"));
            if (top) {
                g.drawLine(x, y, x + width, y);
            } else {
                g.drawLine(x + width - 1, y, x + width - 1, y + height);
            }
        }
    }
}
