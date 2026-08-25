package atri.palaash.jforge.ui.inspector;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class SliderField extends JPanel {
    private final JSlider slider;
    private final JSpinner spinner;

    public SliderField(String label, int min, int max, int initial) {
        super(new BorderLayout(4, 4));
        setBackground(DesignTokens.bgSurface());
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        JLabel l = new JLabel(label);
        l.setFont(DesignTokens.fontCaption());
        l.setForeground(DesignTokens.fgMuted());
        add(l, BorderLayout.NORTH);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        slider = new JSlider(min, max, initial);
        slider.setBackground(DesignTokens.bgSurface());
        slider.setPaintTicks(false);
        slider.setPreferredSize(new Dimension(120, 20));
        spinner = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
        spinner.setFont(DesignTokens.fontCaption());
        ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().setColumns(3);
        slider.addChangeListener(e -> {
            if (!spinner.getValue().equals(slider.getValue())) spinner.setValue(slider.getValue());
        });
        spinner.addChangeListener(e -> {
            int v = (Integer) spinner.getValue();
            if (slider.getValue() != v) slider.setValue(v);
        });
        row.add(slider, BorderLayout.CENTER);
        row.add(spinner, BorderLayout.EAST);
        add(row, BorderLayout.CENTER);
    }

    public int getValue() { return (Integer) spinner.getValue(); }
    public void setValue(int v) { spinner.setValue(v); slider.setValue(v); }
    public void addChangeListener(ChangeListener l) { spinner.addChangeListener(l); slider.addChangeListener(l); }
    public JSlider getSlider() { return slider; }
    public JSpinner getSpinner() { return spinner; }
}
