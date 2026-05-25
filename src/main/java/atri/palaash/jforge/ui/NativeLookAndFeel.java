package atri.palaash.jforge.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Insets;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

/**
 * Centralized look-and-feel management with dark mode auto-detection
 * and runtime toggling. Uses FlatLaf on every platform, with macOS-specific
 * themes when available (FlatMacLightLaf / FlatMacDarkLaf).
 */
public final class NativeLookAndFeel {

    /** Whether the dark theme is currently active. */
    private static boolean darkMode;
    /** Cached OS-level dark-mode preference, or {@code null} if not yet probed. */
    private static Boolean systemDarkMode;

    /** Utility class — prevent instantiation. */
    private NativeLookAndFeel() {
    }

    /** Apply the look-and-feel once at startup (auto-detects dark mode). */
    public static void apply() {
        darkMode = isSystemDarkMode();
        applyTheme(darkMode);
    }

    /** Returns {@code true} if the dark theme is currently active. */
    public static boolean isDarkMode() {
        return darkMode;
    }

    /** Switch theme at runtime and refresh every open window. */
    public static void setDarkMode(boolean dark) {
        if (darkMode == dark) {
            return;
        }
        darkMode = dark;
        applyTheme(dark);
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.repaint();
        }
    }

    /** Flip between dark and light mode at runtime. */
    public static void toggleDarkMode() {
        setDarkMode(!darkMode);
    }

    /* ------------------------------------------------------------------ */

    /**
     * Install the FlatLaf look-and-feel for the requested mode.
     * On macOS it first attempts the native FlatMac* themes and falls back
     * to the standard FlatLaf themes if the Mac variants are not available.
     *
     * @param dark {@code true} for dark mode, {@code false} for light mode
     */
    private static void applyTheme(boolean dark) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            // macOS: try the platform-native FlatMac* themes for a more native feel
            if (os.contains("mac")) {
                try {
                    String className = dark
                            ? "com.formdev.flatlaf.themes.FlatMacDarkLaf"
                            : "com.formdev.flatlaf.themes.FlatMacLightLaf";
                    Class.forName(className).getMethod("setup").invoke(null);
                    configureDefaults();
                    return;
                } catch (Exception ignored) {
                    // Mac themes unavailable in this FlatLaf build — fall through.
                }
            }
            // Standard FlatLaf themes for non-macOS platforms or as fallback
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            configureDefaults();
        } catch (Exception ex) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception fallback) {
                throw new IllegalStateException("Unable to initialize look and feel", fallback);
            }
        }
    }

    /** Tweak FlatLaf UI defaults for a polished, modern feel. */
    private static void configureDefaults() {
        /* Shape — rounded corners following macOS conventions */
        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("CheckBox.arc", 4);

        /* Scrollbar — thin thumb, rounded */
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollPane.smoothScrolling", true);

        /* Title bar — unified background (macOS style) */
        UIManager.put("TitlePane.unifiedBackground", true);

        /* Table */
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));

        /* Focus indicators — subtle blue ring */
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.innerFocusWidth", 0);

        /* Button padding — Apple-style generous padding */
        UIManager.put("Button.margin", new Insets(4, 14, 4, 14));

        /* ComboBox */
        UIManager.put("ComboBox.padding", new Insets(3, 6, 3, 6));

        /* List — sidebar-friendly defaults */
        UIManager.put("List.selectionArc", 8);

        /* Separator */
        UIManager.put("Separator.stripeWidth", 1);
    }

    /* ------------------------------------------------------------------ */

    /** Best-effort detection of the OS-level dark mode setting. */
    public static boolean isSystemDarkMode() {
        if (systemDarkMode != null) {
            return systemDarkMode;
        }
        systemDarkMode = detectSystemDarkMode();
        return systemDarkMode;
    }

    /**
     * Probe the operating system's dark-mode setting.
     * <p>
     * Checks macOS via the {@code AppleInterfaceStyle} defaults key, Windows
     * via the registry theme key, Linux via the {@code GTK_THEME} environment
     * variable, and finally a system property override ({@code jforge.theme}).
     *
     * @return {@code true} if the OS appears to be in dark mode
     */
    private static boolean detectSystemDarkMode() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        // macOS: read the global "AppleInterfaceStyle" preference via the `defaults` command
        if (os.contains("mac")) {
            try {
                Process process = new ProcessBuilder(
                        "defaults", "read", "-g", "AppleInterfaceStyle").start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line = reader.readLine();
                    process.waitFor();
                    return line != null && line.toLowerCase(Locale.ROOT).contains("dark");
                }
            } catch (Exception ignored) {
                // defaults command failed — probably not macOS or sandboxed
            }
        }

        // Windows: query the "AppsUseLightTheme" registry value
        // 0x0 means dark mode is enabled
        if (os.contains("win")) {
            try {
                Process process = new ProcessBuilder(
                        "reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme").start();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String output = reader.lines().reduce("", (a, b) -> a + " " + b);
                    process.waitFor();
                    return output.contains("0x0");
                }
            } catch (Exception ignored) {
                // registry query failed — probably not Windows or access denied
            }
        }

        // Linux / GTK: check the GTK_THEME environment variable
        String gtkTheme = System.getenv("GTK_THEME");
        if (gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark")) {
            return true;
        }

        // Last resort: allow the user to override via system property
        String override = System.getProperty("jforge.theme", "").toLowerCase(Locale.ROOT);
        return "dark".equals(override);
    }
}
