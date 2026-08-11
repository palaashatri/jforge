package atri.palaash.jforge.cli;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.api.JForge;

import java.io.IOException;
import java.util.Arrays;

/**
 * Headless command-line interface that reuses the same engine as the
 * desktop UI through the {@link JForge} facade.
 *
 * <pre>
 * jforge model list
 * jforge generate --model sd_v15_onnx --prompt "a cat" --steps 20 --seed 42 --width 512 --height 512
 * </pre>
 *
 * No UI classes are loaded on the CLI path.
 */
public final class JForgeCli {

    private JForgeCli() {
    }

    public static void main(String[] args) {
        int exit = new JForgeCli().run(args);
        System.exit(exit);
    }

    int run(String[] args) {
        if (args.length == 0) {
            printUsage();
            return 1;
        }
        String command = args[0];
        try (JForge forge = JForge.create()) {
            switch (command) {
                case "model" -> {
                    return model(forge, Arrays.copyOfRange(args, 1, args.length));
                }
                case "generate" -> {
                    return generate(forge, Arrays.copyOfRange(args, 1, args.length));
                }
                default -> {
                    System.out.println("Unknown command: " + command);
                    printUsage();
                    return 1;
                }
            }
        } catch (IOException e) {
            System.out.println("[jforge] Failed to initialise engine: " + e.getMessage());
            return 1;
        }
    }

    private int model(JForge forge, String[] args) {
        if (args.length >= 1 && "list".equals(args[0])) {
            forge.registry().allModels().stream()
                    .sorted((a, b) -> a.id().compareTo(b.id()))
                    .forEach(d -> System.out.println(d.id() + "\t" + d.taskType().displayName() + "\t" + d.displayName()));
            return 0;
        }
        System.out.println("Usage: jforge model list");
        return 1;
    }

    private int generate(JForge forge, String[] args) {
        String model = value(args, "model", null);
        String prompt = value(args, "prompt", "");
        int steps = intValue(args, "steps", 20);
        int seed = intValue(args, "seed", -1);
        int width = intValue(args, "width", 512);
        int height = intValue(args, "height", 512);

        if (model == null) {
            System.out.println("Usage: jforge generate --model <id> --prompt \"...\" [--steps N] [--seed N] [--width N] [--height N]");
            System.out.println("Known models:");
            forge.registry().allModels().forEach(d -> System.out.println("  " + d.id()));
            return 1;
        }

        GenerationRequest.Builder builder = GenerationRequest.builder()
                .model(model)
                .prompt(prompt)
                .steps(steps)
                .width(width)
                .height(height);
        if (seed >= 0) {
            builder.seed(seed);
        }

        System.out.println("[jforge] Generating: " + prompt);
        long start = System.nanoTime();
        GenerationResult result = forge.generate(builder.build());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        if (result.success()) {
            result.images().forEach(image ->
                    System.out.println("[jforge] Wrote " + image.path() + " (" + elapsedMs + " ms)"));
            return 0;
        }
        System.out.println("[jforge] Failed: " + result.error());
        return 1;
    }

    static String value(String[] args, String key, String fallback) {
        for (int i = 0; i < args.length - 1; i++) {
            if (("--" + key).equals(args[i])) {
                return args[i + 1];
            }
        }
        return fallback;
    }

    static int intValue(String[] args, String key, int fallback) {
        String raw = value(args, key, null);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            System.out.println("[jforge] Invalid value for --" + key + ": " + raw);
            return fallback;
        }
    }

    private static void printUsage() {
        System.out.println("jforge — local generative media engine (headless)");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  jforge model list");
        System.out.println("  jforge generate --model <id> --prompt \"...\" [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --steps N   denoising steps (default 20)");
        System.out.println("  --seed N    deterministic seed (default: random)");
        System.out.println("  --width N   output width (default 512)");
        System.out.println("  --height N  output height (default 512)");
    }
}