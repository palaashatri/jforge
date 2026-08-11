# JForge

[![Build JForge](https://github.com/palaashatri/jforge/actions/workflows/build.yml/badge.svg)](https://github.com/palaashatri/jforge/actions/workflows/build.yml)

A local-first, JVM-based generative-media workstation for Windows,
macOS, and Linux. Runs Stable Diffusion family models and Real-ESRGAN
upscaling through ONNX Runtime with automatic GPU execution-provider
selection (CoreML, CUDA, TensorRT, DirectML, OpenVINO, ROCm).

> Status: **early engine milestone (M0) in progress.** See
> [TRUTH.md](TRUTH.md) for the honest readiness ledger and
> [AGENTS.md](AGENTS.md) for the end-state product contract. This README
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

## Not yet built (honest list)
The following are **planned** per [AGENTS.md](AGENTS.md) but do not exist
yet: infinite canvas / layers, img2img, inpainting/outpainting, LoRA,
ControlNet, training, video, CLI/server/worker, embeddable Java API,
plugins, Compose desktop UI, installers, benchmark harness. Do not
report these as working.

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
# Universal build (CPU + CoreML)
mvn clean package -DskipTests

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
mvn test
```

> Tests are currently minimal (negative-path failure handling). The
> scheduler, tokenizer, and golden-inference suites are being added as
> part of the M0 engineering milestone.

## CI / CD

GitHub Actions builds both JAR variants on push/PR (see
[.github/workflows/build.yml](.github/workflows/build.yml)). Packaged
artifacts are uploaded; version tags create GitHub Releases.

## Requirements

- **Java 21** or later
- **macOS 10.15+** for CoreML acceleration (M-series recommended)
- **CUDA 12 + cuDNN** for NVIDIA GPU acceleration (RTX 30xx+ recommended)
- **Python 3.8+** (optional) for PyTorch → ONNX model conversion

## Notes

- Runtime inference is pure Java — ONNX Runtime with GPU fallback. No Python bridge at inference time.
- If a model requires external tensor files (e.g. `weights.pb`), import the complete ONNX bundle into the model directory.