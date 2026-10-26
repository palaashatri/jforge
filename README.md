# JForge

[![Build JForge](https://github.com/palaashatri/jforge/actions/workflows/build.yml/badge.svg)](https://github.com/palaashatri/jforge/actions/workflows/build.yml)

A local-first, JVM-based generative-media workstation for Windows,
macOS, and Linux. Runs Stable Diffusion family models and Real-ESRGAN
upscaling through ONNX Runtime with automatic GPU execution-provider
selection (CoreML, CUDA, TensorRT, DirectML, OpenVINO, ROCm).

> Status: **engine milestones M0–M2 done; later milestones are scaffolding only.** See
> [TRUTH.md](TRUTH.md) (read the correction at the top first),
> [docs/PRODUCT_GOAL.txt](docs/PRODUCT_GOAL.txt) for the end-state product brief, and
> [AGENTS.md](AGENTS.md) for contributor instructions. This README
> describes what actually works today.

## What works today

### Image generation
- **Text → Image**: Stable Diffusion v1.5, SD Turbo, SDXL Turbo, SDXL Base 1.0, SD 3.5 (MMDiT)
- **Image upscaling**: Real-ESRGAN 4× super-resolution with before/after preview and tiled inference for large images
- Negative prompts + prompt weighting
- Seed control (repeat a seed to reproduce an image on the same backend/config)
- Batch generation (N images per prompt)
- Aspect ratio presets + custom size fields
- Style presets (cinematic, sketch, product, etc.)
- History gallery with metadata (seed, model, settings)
- Export pipeline logs for debugging

### SD 3.5 support
- MMDiT transformer architecture with Flow Matching Euler scheduler
- Triple text encoding: CLIP-L (768d) + CLIP-G (1280d) + T5-XXL (4096d)
- Built-in T5 tokenizer (SentencePiece/Unigram with Viterbi segmentation)
- 16-channel latent space

### Model management
- **Automatic HuggingFace discovery**: finds ONNX and PyTorch Stable Diffusion + ESRGAN models
- **One-click download** with resume, retry, and stall detection
- **PyTorch → ONNX auto-conversion**: downloading a PyTorch model triggers automatic conversion via managed Python venv
- Manual ONNX model import
- Gated model support with HuggingFace token authentication
- Local model storage in `~/.jforge-models`

### GPU acceleration
Intelligent execution provider selection — JForge probes available EPs at runtime and picks the best one:

| Platform | Priority (highest → lowest) |
|---|---|
| **macOS** | CoreML (GPU + ANE + CPU) → CPU |
| **Windows** | TensorRT-RTX → TensorRT → CUDA → DirectML → OpenVINO → CPU |
| **Linux** | TensorRT → CUDA → ROCm → OpenVINO → CPU |

Override with `-Djforge.ep=cuda` (or any EP key) to force a specific provider.

### UI
- Native look-and-feel: system-native on macOS, FlatLaf with dark/light detection on Windows/Linux
- Async generation using virtual threads (UI never blocks during inference)
- Per-step progress with timing and ETA
- Session and tokenizer caching for fast repeated inference

### Headless CLI & embeddable Java API
The same engine runs without any UI:

```bash
# list registered models
java -jar jforge-universal.jar model list

# generate (same flags as the desktop form)
java -jar jforge-universal.jar generate --model sd_v15_onnx --prompt "a cat" --steps 20 --seed 42 --width 512 --height 512

# upscale an image
java -jar jforge-universal.jar upscale --model realesrgan --image photo.png
```

Java applications can embed the engine directly — no Swing/UI classes on
this path:

```java
try (JForge forge = JForge.create()) {
    GenerationResult result = forge.generate(
            GenerationRequest.builder()
                    .model("sd_v15_onnx")
                    .prompt("a cat")
                    .steps(20)
                    .build());
    System.out.println(result.images().get(0).path());
}
```

## Not yet built (honest list)
The following are **planned** per [docs/PRODUCT_GOAL.txt](docs/PRODUCT_GOAL.txt). Some have
scaffolding classes in the source tree, but none is wired into the app or CLI yet: infinite canvas / layers, img2img, inpainting/outpainting, LoRA,
ControlNet, training, video, REST server/worker, plugins, workflow graph,
Compose desktop UI, installers, benchmark harness. Do not report these
as working.

## Roadmap

In priority order — each item is something a user would notice:

1. **Image-to-image and inpainting in the workspace**: the request API already accepts an input image; wire it to the canvas with a mask brush.
2. **LoRA support end to end**: apply `LoRAStack` weights in the SD 1.5/SDXL pipelines, with a picker in the inspector.
3. **FLUX.1 schnell** as the first modern model family, wired to the existing pipeline SPI with a golden-image test.
4. **Generation queue with cancel and a history you can re-run** (manifest round-tripping already exists).
5. **Installers**: `jpackage` builds for macOS, Windows and Linux so users don't need a JDK.
6. **Delete or finish the remaining scaffolding** (`video/`, `training/`, `plugin/`, `comfy/`, `server/`) so the codebase only contains features that work.

## Downloads

Pre-built fat JARs are available from [GitHub Releases](https://github.com/palaashatri/jforge/releases):

| JAR | GPU Support | Use When |
|---|---|---|
| `jforge-universal.jar` | macOS CoreML (M-series GPU/ANE), CPU everywhere | macOS, or Windows/Linux without NVIDIA GPU |
| `jforge-nvidia.jar` | CUDA + TensorRT (Windows/Linux) | Windows/Linux with NVIDIA GPU + CUDA installed |

> **Note**: DirectML (AMD/Intel on Windows), OpenVINO (Intel), and ROCm (AMD on Linux) are auto-detected at runtime if the native libraries are installed on the system.

```bash
java -jar jforge-universal.jar
java -jar jforge-nvidia.jar
```

## Build from source

Requires **Java 21+** and **Maven 3.8+**.

```bash
# Universal build (CPU + CoreML). On Linux/Windows a plain `mvn package`
# builds the GPU variant (OS-activated profile), so name the artifact explicitly:
mvn clean package -DskipTests -Dort.artifactId=onnxruntime

# NVIDIA GPU build (CUDA + TensorRT)
mvn clean package -DskipTests -Dort.artifactId=onnxruntime_gpu

# Force CPU-only
mvn clean package -DskipTests -Dort.artifactId=onnxruntime
```

### Run from source

```bash
mvn clean compile exec:java
```

### Run tests

```bash
# CPU-only test run
mvn -B test -Dort.artifactId=onnxruntime
```

The suite (139 tests) covers the typed request API, scheduler math —
including hand-computed golden values for every schedule and step
formula — CLIP/T5 tokenizers against real fixtures, the legacy engine
bridge, CLI argument parsing, and manifest round-tripping.

## CI / CD

GitHub Actions runs `mvn test` plus both JAR builds on push/PR to
`main` (see [.github/workflows/build.yml](.github/workflows/build.yml)).
Packaged artifacts are uploaded; version tags create GitHub Releases.

## Requirements

- **Java 21** or later
- **macOS 10.15+** for CoreML acceleration (M-series recommended)
- **CUDA 12 + cuDNN** for NVIDIA GPU acceleration (RTX 30xx+ recommended)
- **Python 3.8+** (optional) for PyTorch → ONNX model conversion

## Notes

- Runtime inference is pure Java — ONNX Runtime with GPU fallback. No Python bridge at inference time.
- If a model requires external tensor files (e.g. `weights.pb`), import the complete ONNX bundle into the model directory.

## Planned SLOPOS, PARAMA and edge-device support

jforge is an original project considered for SLOPOS's **optional first-party catalog**, not required in its minimal image. Linux/SLOPOS GUI support must be demonstrated through real desktop integration; **PARAMA remains a conditional native source-port goal** after its actual process, graphics, files, ABI and toolkit dependencies exist. This is a platform roadmap, not a build/compatibility claim.

**Raspberry Pi Zero 2 W efficiency:** Keep the model manager, gallery metadata and app UI lightweight and lazy, preflight Java heap + ONNX native/tensor allocations + model weights + GPU/CPU backend capacity before loading. Stable Diffusion/SDXL and 4× upscaling are **not assumed feasible** under 512 MB; show an explicit unsupported/local-resource explanation and permit user-approved remote/offloaded execution only behind a separate provider, never automatic upload. Avoid loading model lists/thumbnails/GPU EPs at idle and cancel cleanly on low memory.

See [AGENTS.md](AGENTS.md#future-sloposparama-platforms-and-raspberry-pi-zero-2-w-efficiency) for measurable acceptance and restrictions. No actual Pi Zero 2 W or PARAMA acceptance has been performed.
