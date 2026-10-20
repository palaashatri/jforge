package atri.palaash.jforge.ui.workspace;

import atri.palaash.jforge.ui.design.DesignTokens;

import javax.swing.*;
import java.awt.*;

public class WorkspaceShell extends JPanel {
    private final ToolRail toolRail;
    private final CanvasPanel canvas;
    private final InspectorPanel inspector;
    private final FilmstripPanel filmstrip;
    private final JPanel centerHost = new JPanel(new CardLayout());
    private static final String CARD_CANVAS = "canvas";

    public WorkspaceShell(ToolRail.ToolListener toolListener) {
        super(new BorderLayout());
        this.toolRail = new ToolRail(toolListener);
        this.canvas = new CanvasPanel();
        this.inspector = new InspectorPanel();
        this.filmstrip = new FilmstripPanel();

        centerHost.add(canvas, CARD_CANVAS);
        centerHost.setBackground(DesignTokens.bgCanvas());

        add(toolRail, BorderLayout.WEST);
        add(inspector, BorderLayout.EAST);
        add(filmstrip, BorderLayout.SOUTH);
        add(centerHost, BorderLayout.CENTER);
    }

    public ToolRail getToolRail() { return toolRail; }
    public CanvasPanel getCanvas() { return canvas; }
    public InspectorPanel getInspector() { return inspector; }
    public FilmstripPanel getFilmstrip() { return filmstrip; }

    public void setCenterComponent(JComponent c) {
        centerHost.removeAll();
        if (c != null) centerHost.add(c, CARD_CANVAS);
        else centerHost.add(canvas, CARD_CANVAS);
        revalidate(); repaint();
    }

    public void setInspectorContent(JComponent c) { inspector.setContent(c); }
}
