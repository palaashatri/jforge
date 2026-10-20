package atri.palaash.jforge.ui.workspace;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceShellTest {
    @Test void shellCreatesAllRegions() {
        WorkspaceShell shell = new WorkspaceShell(null);
        assertNotNull(shell.getToolRail());
        assertNotNull(shell.getCanvas());
        assertNotNull(shell.getInspector());
        assertNotNull(shell.getFilmstrip());
    }
    @Test void toolRailDefaultSelection() {
        ToolRail rail = new ToolRail(null);
        assertEquals(ToolRail.Tool.SELECT, rail.getSelectedTool());
    }
    @Test void canvasZoomClamps() {
        CanvasPanel canvas = new CanvasPanel();
        canvas.setZoom(100.0);
        assertTrue(canvas.getZoom() <= 8.0);
        canvas.setZoom(0.001);
        assertTrue(canvas.getZoom() >= 0.1);
        canvas.fitToView();
        assertEquals(1.0, canvas.getZoom(), 1e-9);
    }
    @Test void inspectorAndFilmstripAcceptContent() {
        WorkspaceShell shell = new WorkspaceShell(null);
        JPanel p = new JPanel();
        shell.setInspectorContent(p);
        shell.setCenterComponent(p);
        assertDoesNotThrow(() -> shell.getFilmstrip().setInfo("test"));
    }
}
