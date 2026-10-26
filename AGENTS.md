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
- `java -jar jforge-*.jar <args>` now dispatches to the CLI when arguments are provided (JForgeApp.main checks args and calls JForgeCli.main). Without args it still opens the GUI.
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

## Future SLOPOS/PARAMA platforms and Raspberry Pi Zero 2 W efficiency

**FUTURE DESIGN TARGET / NOT IMPLEMENTED.** jforge is an independently authored candidate for the **optional first-party SLOPOS local image generation/upscaling workbench**. Preserve Java 21 Swing desktop, Java CLI and ONNX Runtime. No present SLOPOS package, Linux AArch64 Pi validation or PARAMA native port is implied. Maintain existing milestone/tech-stack rules and inspect all imported code, fonts, runtime libraries and models for license/provenance before first-party distribution.

- **SLOPOS/Linux:** Linux/SLOPOS file dialogs, drag/drop, image import/export, GPU/CPU capability discovery, model inventory/provenance, app menu commands, clipboard/accessibility and original branded app identity; preserve standalone Windows/macOS support and a truthful available/unavailable status.
- **Edge efficiency on 4× Cortex-A53 and 512 MB shared memory:** Keep the model manager, gallery metadata and app UI lightweight and lazy, preflight Java heap + ONNX native/tensor allocations + model weights + GPU/CPU backend capacity before loading. Stable Diffusion/SDXL and 4× upscaling are **not assumed feasible** under 512 MB; show an explicit unsupported/local-resource explanation and permit user-approved remote/offloaded execution only behind a separate provider, never automatic upload. Avoid loading model lists/thumbnails/GPU EPs at idle and cancel cleanly on low memory.
- **Native PARAMA / SURYA:** Future source-port would need JVM 21+, Swing/AWT or an approved alternate client, compatible ONNX Runtime native ABI with actual CPU kernels or GPU drivers, network/download/storage, filesystem, SURYA surfaces and memory accounting. Native inference is separately gated; do not claim diffusion runs on PARAMA or the Pi.
- **Acceptance:** Measure no-model cold/warm UI start and idle PSS/CPU on actual Pi Zero 2 W if UI/runtime boots; fake/oversized model manifest fails preflight without OOM, cancel cleans allocated memory, and output format correctness is verified on supported larger hardware.
- **Instrumentation and boundaries:** Each target documents actual distro/ABI/OS, hardware, dependencies, model versions, cold/warm launch, idle CPU, process tree PSS and peak RSS, whole-system `MemAvailable`, UI input p95, output fidelity and negative/OOM/restart tests. Use bounded streaming work, progress/backpressure, cancellation and clean recovery instead of UI-blocking calls; network/disk delay may take longer but should not block control inputs. Real Pi board acceptance differs from cross-compilation/QEMU. Optional model/runtime downloads are not part of SLOPOS's minimal 512 MB image. PARAMA native readiness must be tested separately from Linux compatibility personality research.
