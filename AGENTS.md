# AGENTS.md — JForge

JForge is a Java 21 Swing desktop app (plus a headless CLI and an embeddable Java API) for local image generation on ONNX Runtime: Stable Diffusion 1.5, SD Turbo, SDXL Base/Turbo, SD 3.5, and Real-ESRGAN upscaling, with a Hugging Face model downloader and optional PyTorch→ONNX conversion through a managed Python venv. Models live in `~/.jforge-models`.

The long-term product brief ("competitive with Draw Things") is [`docs/PRODUCT_GOAL.txt`](docs/PRODUCT_GOAL.txt). It describes the destination, not the current state.

## Current state (2026-10-20, after PR #5 was merged)

What is real and wired into the app:
- Per-architecture pipelines (`inference/Sd15OnnxPipeline`, `SdTurbo…`, `SdxlBase…`, `SdxlTurbo…`, `Sd3OnnxPipeline`, `RealEsrganOnnxPipeline`) behind `GenericOnnxService` (now ~600 lines), shared scheduler math with golden tests, deterministic seeds, typed `GenerationRequest`.
- `api/JForge` facade, `cli/JForgeCli`, model ingestion (`model/ingest`: safetensors headers, Diffusers index), the new workspace UI (`ui/workspace`, design tokens, command palette, status bar).
- CI runs `mvn test` on Linux and Windows (139 `@Test` methods) and builds the universal and NVIDIA jars.

What exists only as unwired scaffolding — **no code outside its own package uses it**:
- `canvas/`, `lora/`, `control/` (ControlNet), `memory/`, `training/`, `video/`, `plugin/`, `server/` (REST), `comfy/`, `script/`, `perf/`, `security/`, `hardware/`.
- `FluxPipeline`, `QwenImagePipeline`, `ZImagePipeline` and `VideoDiffusionPipeline` load a bundle and then return "execution wiring pending". `video/VideoEncoder` writes a JSON file, not a video. `hardware/HardwareMatrix` hard-codes GPUs as "verified" without any test behind it.
- `TRUTH.md` reports 100/100; that score is withdrawn (see the correction at the top of `TRUTH.md`).

Known bugs:
- `java -jar jforge-*.jar <args>` always opens the GUI: the jar's main class is `app.JForgeApp`, which ignores arguments, so the CLI is reachable only with `java -cp <jar> atri.palaash.jforge.cli.JForgeCli …`.
- The `gpu-linux` / `gpu-windows` Maven profiles auto-activate, so a plain `mvn package` on Linux or Windows builds the **GPU** variant. Pass `-Dort.artifactId=onnxruntime` for the CPU build.

## Commands

Requirements: JDK 21+, Maven 3.8+. Python 3.8+ only for PyTorch→ONNX conversion.

```bash
mvn -B test -Dort.artifactId=onnxruntime                    # what CI runs
mvn clean compile exec:java                                  # run the desktop app from source
mvn clean package -DskipTests -Dort.artifactId=onnxruntime   # CPU (+ CoreML on macOS) fat jar
mvn clean package -DskipTests -Dort.artifactId=onnxruntime_gpu -Djar.classifier=-nvidia   # CUDA/TensorRT fat jar
java -cp target/jforge-*.jar atri.palaash.jforge.cli.JForgeCli model list   # headless CLI
```

Force an execution provider with `-Djforge.ep=cpu|coreml|cuda|tensorrt|directml|openvino|rocm`.

## Layout (`src/main/java/atri/palaash/jforge/`)

| Package | What it does |
|---|---|
| `app/` | Desktop entry point |
| `api/` | `JForge` facade, `GenerationRequest`/`Result`/`Manifest` |
| `engine/` | Pipeline/scheduler/backend SPIs, scheduler math, latent noise, legacy bridge |
| `inference/` | Per-architecture ONNX pipelines and `GenericOnnxService` |
| `model/`, `storage/` | Registry, ingestion, Hugging Face download, storage, PyTorch→ONNX converter |
| `tokenize/` | CLIP and T5 tokenizers |
| `ui/` | MainFrame, workspace shell, design tokens, panels |
| `cli/` | Headless CLI |
| everything else | Scaffolding — see "Current state" |

## Rules

1. **A feature is done only when a user can reach it and it produces real output.** Scaffolding stays out of README, release notes and `TRUTH.md` scores until it is wired into the app or CLI and has a test that runs a real (small) model or a golden fixture.
2. Don't add new model families or features as more scaffolding packages. Finish and wire one at a time, or delete it.
3. Don't claim GPU support from provider enumeration. Record which hardware was actually tested, by whom, and when.
4. Never block the Swing EDT with model loading, inference or downloads; post results back with `SwingUtilities.invokeLater`.
5. Downloads go to temp files and are moved into place only when complete. Validate archive paths, sanitise model IDs used in paths, and never log Hugging Face tokens.
6. Pin dependency versions in `pom.xml`. Don't commit model weights, generated images or `target/`.

## Git workflow and done

- Branch and open a PR unless the owner explicitly asks for a direct push to `main`.
- `mvn -B test -Dort.artifactId=onnxruntime` passes; if you touched inference, the app generates one image with a small model (SD Turbo) and you say so in the PR.
- README describes only what works on `main`.
