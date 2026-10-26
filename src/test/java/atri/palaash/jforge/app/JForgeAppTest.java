package atri.palaash.jforge.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JForgeAppTest {

    @Test
    void shouldRunCliWhenArgsPresent() {
        assertTrue(JForgeApp.shouldRunCli(new String[]{"model", "list"}));
    }

    @Test
    void shouldNotRunCliWhenArgsMissing() {
        assertFalse(JForgeApp.shouldRunCli(new String[0]));
        assertFalse(JForgeApp.shouldRunCli(null));
    }
}
