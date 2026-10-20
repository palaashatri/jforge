# AGENTS.md — JForge

JForge is a Java 21 Swing desktop app for local image generation (Stable Diffusion 1.5, SD Turbo, SDXL, SD 3.5) and Real-ESRGAN upscaling on ONNX Runtime, with a Hugging Face model downloader and optional PyTorch→ONNX conversion through a managed Python venv. Models live in `~/.jforge-models`.

## Current state (2026-10-20)

- `main` is small (≈8.8k lines) and works as a single-window app, but `inference/GenericOnnxService.java` is a 3,266-line class holding every pipeline, scheduler and tokenizer.
- `main` has **2 unit tests**, and CI builds with `-DskipTests`, so tests never run in CI.
- Open PR #5 (`milestone-m0-engine-correctness`) splits the god class into per-architecture pipelines with scheduler golden tests (its M0–M2 commits). Its later commits add **stub** pipelines and modules (FLUX, Qwen, Z-Image and video return "execution wiring pending"; the video encoder writes JSON, not video; LoRA, ControlNet, canvas and memory manager are not wired into the app; a hard-coded "verified" GPU table) while `TRUTH.md` scores them 100/100. Treat that branch as a source of good M0–M2 work, not as finished features.

## Commands

Requirements: JDK 21+, Maven 3.8+. Python 3.8+ only for PyTorch→ONNX conversion.

```bash
mvn -B test                                                  # run the tests (CI does not do this yet)
mvn clean compile exec:java                                  # run from source
mvn clean package -DskipTests -Dort.artifactId=onnxruntime   # CPU (+ CoreML on macOS) fat jar
mvn clean package -DskipTests -Dort.artifactId=onnxruntime_gpu -Djar.classifier=-nvidia   # CUDA/TensorRT fat jar
java -jar target/jforge-1.0.0-SNAPSHOT*.jar
```

Gotcha: the `gpu-linux` and `gpu-windows` Maven profiles auto-activate on those OSes, so a plain `mvn package` there builds the **GPU** variant. Pass `-Dort.artifactId=onnxruntime` for the CPU build. Force an execution provider at runtime with `-Djforge.ep=cpu|coreml|cuda|tensorrt|directml|openvino|rocm`.

## Layout (`src/main/java/atri/palaash/jforge/`)

| Package | What it does |
|---|---|
| `app/JForgeApp` | Entry point, look-and-feel, startup |
| `inference/` | `InferenceService` interface, `ServiceFactory`, `GenericOnnxService` (all pipelines, schedulers, CLIP/T5 tokenizers, EP selection) |
| `model/` | `ModelRegistry`, `ModelDescriptor`, `TaskType` |
| `storage/` | Hugging Face discovery/download (resume, retry, stall detection), local storage, PyTorch→ONNX converter |
| `ui/` | `MainFrame`, text-to-image, upscale, model manager, history and prompt-library panels |
| `scripts/` | Python helpers for conversion/export |

## Rules

1. **Inference correctness before features.** Seeds must be deterministic, schedulers must match reference values, and each model family needs its own pipeline. Don't add new model families or UI features into `GenericOnnxService`; split it first (PR #5 shows how).
2. **No stubs presented as features.** A pipeline, provider or tool counts only when it produces real output from a real model and has a test. Unfinished work stays out of the UI and README, or is labelled experimental.
3. Don't claim GPU support from provider enumeration. Record which hardware was actually tested.
4. Never block the Swing EDT with model loading, inference or downloads; use background executors (virtual threads are fine) and post results back with `SwingUtilities.invokeLater`.
5. Downloads go to temp files and are moved into place only when complete. Validate archive entry paths, sanitise model IDs used in paths, and never log Hugging Face tokens.
6. Pin dependency versions in `pom.xml`. Don't commit model weights, generated images or `target/`.

## Git workflow and done

- Never push to `main`; branch and open a PR.
- `mvn -B test` passes, and the app starts and generates one image with a small model (e.g. SD Turbo) if you touched inference.
- README describes only what works on `main`.
