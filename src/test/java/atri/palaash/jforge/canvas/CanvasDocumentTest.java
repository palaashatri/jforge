package atri.palaash.jforge.canvas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CanvasDocumentTest {
    @Test void addAndFindLayer() {
        CanvasDocument doc = new CanvasDocument(512, 512);
        ImageLayer img = new ImageLayer("a.png", 256, 256);
        doc.addLayer(img);
        assertEquals(1, doc.layerCount());
        assertTrue(doc.findById(img.id()).isPresent());
    }

    @Test void moveAndResize() {
        CanvasDocument doc = new CanvasDocument();
        ImageLayer img = new ImageLayer("a.png", 100, 100);
        doc.addLayer(img);
        doc.moveLayer(img.id(), 50, 60);
        assertEquals(50, doc.findById(img.id()).get().x());
        doc.resizeLayer(img.id(), 200, 200);
        assertEquals(200, doc.findById(img.id()).get().width());
    }

    @Test void undoRedo() {
        CanvasDocument doc = new CanvasDocument();
        ImageLayer a = new ImageLayer("a.png", 100, 100);
        doc.addLayer(a);
        assertEquals(1, doc.layerCount());
        assertTrue(doc.canUndo());
        doc.undo();
        assertEquals(0, doc.layerCount());
        assertTrue(doc.canRedo());
        doc.redo();
        assertEquals(1, doc.layerCount());
    }

    @Test void removeAndReorder() {
        CanvasDocument doc = new CanvasDocument();
        ImageLayer a = new ImageLayer("a.png", 100, 100);
        ImageLayer b = new ImageLayer("b.png", 100, 100);
        doc.addLayer(a); doc.addLayer(b);
        assertEquals(2, doc.layerCount());
        doc.reorderLayer(b.id(), 0);
        assertEquals(b.id(), doc.layers().get(0).id());
        assertTrue(doc.removeLayer(a.id()));
        assertEquals(1, doc.layerCount());
    }

    @Test void allLayerTypesPersist(@TempDir Path tmp) throws Exception {
        CanvasDocument doc = new CanvasDocument(1024, 768);
        doc.addLayer(new ImageLayer("img.png", 512, 512));
        doc.addLayer(new GenerationLayer("a cat", "sd15", 512, 512));
        doc.addLayer(new MaskLayer(512, 512));
        doc.addLayer(new ReferenceLayer("ref.png", 0.8f));
        doc.addLayer(new GuideLayer("grid", 1024, 768));
        doc.addLayer(new GroupLayer("group", List.of(new ImageLayer("x.png", 64, 64))));
        Path file = tmp.resolve("test.jforge");
        doc.saveTo(file);
        assertTrue(Files.exists(file));
        CanvasDocument loaded = CanvasDocument.loadFrom(file);
        assertEquals(6, loaded.layerCount());
        assertEquals("1.0", loaded.version());
        assertEquals(1024, loaded.width());
        assertTrue(loaded.findById(doc.layers().get(0).id()).isPresent());
    }

    @Test void visibilityToggle() {
        CanvasDocument doc = new CanvasDocument();
        ImageLayer img = new ImageLayer("a.png", 100, 100);
        doc.addLayer(img);
        doc.setLayerVisible(img.id(), false);
        assertFalse(doc.findById(img.id()).get().visible());
        assertTrue(doc.canUndo());
        doc.undo();
        assertTrue(doc.findById(img.id()).get().visible());
    }

    @Test void groupLayerChildren() {
        ImageLayer child = new ImageLayer("c.png", 32, 32);
        GroupLayer group = new GroupLayer("myGroup", List.of(child));
        assertEquals(1, group.children().size());
        CanvasDocument doc = new CanvasDocument();
        doc.addLayer(group);
        assertEquals(1, doc.layerCount());
    }
}
