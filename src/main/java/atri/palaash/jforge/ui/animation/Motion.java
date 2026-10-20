package atri.palaash.jforge.ui.animation;

import javax.swing.*;
import java.awt.event.ActionEvent;

public final class Motion {
    private Motion() {}

    public static final int DURATION_FAST = 120;
    public static final int DURATION_MEDIUM = 180;
    public static final int DURATION_SLOW = 250;

    public static boolean isReducedMotion() {
        String v = System.getProperty("jforge.reducedMotion", System.getProperty("jforge.animation", ""));
        if ("off".equalsIgnoreCase(v) || "reduced".equalsIgnoreCase(v) || "true".equalsIgnoreCase(v)) return true;
        Object ui = UIManager.getBoolean("jforge.reducedMotion");
        return Boolean.TRUE.equals(ui);
    }

    public static void animate(int durationMs, float from, float to, java.util.function.Consumer<Float> onFrame, Runnable onDone) {
        if (isReducedMotion() || durationMs <= 0) {
            onFrame.accept(to);
            if (onDone != null) onDone.run();
            return;
        }
        long start = System.currentTimeMillis();
        Timer timer = new Timer(16, null);
        timer.addActionListener(new java.awt.event.ActionListener() {
            @Override public void actionPerformed(ActionEvent e) {
                long elapsed = System.currentTimeMillis() - start;
                float t = Math.min(1f, elapsed / (float) durationMs);
                float eased = easeOutCubic(t);
                float v = from + (to - from) * eased;
                onFrame.accept(v);
                if (t >= 1f) {
                    timer.stop();
                    if (onDone != null) onDone.run();
                }
            }
        });
        timer.start();
    }

    private static float easeOutCubic(float t) {
        float p = t - 1;
        return p * p * p + 1;
    }
}
