package atri.palaash.jforge.ui.inspector;

import atri.palaash.jforge.engine.PipelineCapabilities;
import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class InspectorController {
    private final PromptEditor promptEditor;
    private final PromptEditor negativeEditor;
    private final SliderField stepsField;
    private final SliderField cfgField;
    private final SliderField seedField;
    private final JComboBox<String> schedulerBox;
    private final JComboBox<String> resolutionBox;
    private final JPanel root;

    public InspectorController() {
        promptEditor = new PromptEditor("Describe the image you want…");
        negativeEditor = new PromptEditor("Things to avoid (optional)");
        negativeEditor.getTextArea().setRows(2);
        stepsField = new SliderField("Steps", 1, 50, 20);
        cfgField = new SliderField("CFG", 1, 20, 7);
        seedField = new SliderField("Seed", 0, 2147483647, 42);
        schedulerBox = new JComboBox<>(new String[]{"DDIM","EULER","EULER_ANCESTRAL","FLOW_MATCH_EULER","DISTILLED_EULER","DPM++","LCM"});
        schedulerBox.setFont(DesignTokens.fontCaption());
        resolutionBox = new JComboBox<>(new String[]{"512×512","768×512","512×768","1024×1024","640×480","768×432"});
        resolutionBox.setFont(DesignTokens.fontCaption());

        root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(DesignTokens.bgSurface());
        root.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JPanel promptBody = new JPanel(new BorderLayout(0, 8));
        promptBody.setOpaque(false);
        promptBody.add(promptEditor, BorderLayout.CENTER);
        promptBody.add(wrapLabel("Negative Prompt", negativeEditor), BorderLayout.SOUTH);
        root.add(new CollapsibleSection("Prompt", promptBody));
        root.add(Box.createVerticalStrut(8));

        JPanel genBody = new JPanel();
        genBody.setLayout(new BoxLayout(genBody, BoxLayout.Y_AXIS));
        genBody.setOpaque(false);
        genBody.add(stepsField);
        genBody.add(cfgField);
        genBody.add(seedField);
        genBody.add(labeled("Scheduler", schedulerBox));
        genBody.add(labeled("Resolution", resolutionBox));
        root.add(new CollapsibleSection("Generation", genBody));
        root.add(Box.createVerticalStrut(8));

        JPanel canvasBody = new JPanel(new BorderLayout());
        canvasBody.setOpaque(false);
        JLabel hint = new JLabel("<html><body style='width:220px;color:#8A8A94'>Selection, mask painting, before/after. Drag an image onto canvas for img2img.</body></html>");
        hint.setFont(DesignTokens.fontCaption());
        canvasBody.add(hint, BorderLayout.CENTER);
        root.add(new CollapsibleSection("Canvas", canvasBody));
    }

    private static JPanel labeled(String text, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel l = new JLabel(text);
        l.setFont(DesignTokens.fontCaption());
        l.setForeground(DesignTokens.fgMuted());
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(2,0,2,0));
        return p;
    }

    private static JPanel wrapLabel(String text, JComponent comp) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        JLabel l = new JLabel(text);
        l.setFont(DesignTokens.fontCaption());
        l.setForeground(DesignTokens.fgMuted());
        p.add(l, BorderLayout.NORTH);
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    public JPanel getView() { return root; }
    public PromptEditor promptEditor() { return promptEditor; }
    public PromptEditor negativeEditor() { return negativeEditor; }
    public SliderField stepsField() { return stepsField; }
    public SliderField cfgField() { return cfgField; }
    public SliderField seedField() { return seedField; }
    public JComboBox<String> schedulerBox() { return schedulerBox; }
    public JComboBox<String> resolutionBox() { return resolutionBox; }

    public void bindCapabilities(PipelineCapabilities caps) {
        boolean showCfg = caps.supportsCfg();
        boolean showNeg = caps.supportsNegativePrompt();
        cfgField.setVisible(showCfg);
        negativeEditor.setVisible(showNeg);
        for (java.awt.Component c : root.getComponents()) {
            if (c instanceof CollapsibleSection) c.setVisible(true);
        }
        if (showCfg) cfgField.setToolTipText(null);
        else cfgField.setToolTipText("CFG hidden for this distilled pipeline");
        root.revalidate(); root.repaint();
    }
}
