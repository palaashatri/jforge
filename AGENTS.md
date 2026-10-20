# JForge — Architecture & Product Contract

JForge is a **professional, local-first generative-media workstation and
embeddable JVM inference platform for Windows, Linux, and macOS**, MIT
licensed, engineered to be at least competitive with Draw Things in
functionality and materially better in cross-platform desktop support,
extensibility, JVM integration, workflow flexibility, hardware coverage,
and developer ergonomics.

This document is the immutable end-state product contract. The live
readiness ledger is `TRUTH.md`. The user-facing description of what
works today is `README.md`. Deviations require a genuine technical
justification recorded in `TRUTH.md`.

---

## 1. Product definition (end state)

> JForge is an open-source, MIT-licensed, local-first generative media
> workstation and JVM inference platform. It provides professional image
> generation and editing, an infinite creative canvas, modern
> diffusion/transformer models, LoRA and conditioning workflows,
> hardware-accelerated inference across NVIDIA, AMD, Intel and Apple
> systems, local training, reproducible workflows, CLI/server operation,
> remote GPU workers, plugins, and cross-platform desktop applications
> for macOS, Windows and Linux.

Hard constraints:

- **MIT license.** No GPL/other incompatible code may be imported.
  Feature concepts and public algorithms may be independently
  implemented. Clean-room requirement enforced.
- **JVM-centric.** The inference engine must remain Java/JVM. The UI may
  be Kotlin/Compose; it must never become JavaScript/Electron.
- **Local-first.** No cloud upload without explicit user action. No
  external telemetry unless explicitly opt-in.
- **No fake features.** Placeholders, disabled buttons, and documentation
  do not count as implementation. See `TRUTH.md` status vocabulary.

---

## 2. Module / package architecture

Target module split (a Maven multi-module build; package-level
separation is an acceptable interim while migrating):

| Module | Responsibility |
|---|---|
| `jforge-api` | Public Java API: `GenerationRequest`, `GenerationResult`, builder, manifest types. No UI classes. |
| `jforge-engine` | Pipeline SPI, schedulers, samplers, conditioning, Latents, deterministic randomness. |
| `jforge-models` | `ModelBundle`, model registry, capability metadata, model manifest parse. |
| `jforge-backend-ort` | ONNX Runtime backend (CPU/CUDA/DirectML/OpenVINO/ROCm/TensorRT/CoreML EPs). |
| `jforge-backend-metal` | Native Metal backend (Apple Silicon). |
| `jforge-backend-cuda` | Native CUDA backend. |
| `jforge-backend-openvino` | OpenVINO backend. |
| `jforge-control` | ControlNet, preprocessors, reference/IP-Adapter conditioning. |
| `jforge-lora` | LoRA parse/apply/train, textual inversion. |
| `jforge-training` | LoRA training workspace (dataset, captions, checkpoint/resume). |
| `jforge-canvas` | Document model, layers, undo/redo (UI-independent). |
| `jforge-video` | Video pipelines, timeline, encoding. |
| `jforge-storage` | Model storage, download/verify/checksum, project persistence. |
| `jforge-server` | Headless REST/gRPC server, remote workers, auto-scheduling. |
| `jforge-cli` | Headless CLI reusing the same engine. |
| `jforge-desktop` | Compose Multiplatform Desktop UI. |

Migration rule: package/service boundaries come first; multi-module
splits happen incrementally without breaking the build.

---

## 3. Core abstractions

### 3.1 GenerationRequest

Explicit typed fields. Batch size and inference steps must be separate
values with separate semantics. See `jforge-api`.

### 3.2 GenerationPipeline

```java
public interface GenerationPipeline {
    PipelineDescriptor descriptor();
    PipelineCapabilities capabilities();
    LoadedPipeline load(ModelBundle model, ComputeBackend backend, LoadOptions options) throws Exception;
    GenerationResult generate(LoadedPipeline pipeline, GenerationRequest request,
                             ProgressListener progress, CancellationToken cancellation) throws Exception;
}
```

Separate implementations: `StableDiffusion15Pipeline`,
`StableDiffusionXLPipeline`, `StableDiffusion3Pipeline`, `FluxPipeline`,
`QwenImagePipeline`, `ZImagePipeline`, `ImageUpscalePipeline`,
`VideoDiffusionPipeline`. Shared functionality lives in reusable engine
components, never in one god object.

### 3.3 PipelineCapabilities

Every model/pipeline declares capabilities; the UI is generated from
capabilities, never from hard-coded model-name conditionals:

`TEXT_TO_IMAGE`, `IMAGE_TO_IMAGE`, `INPAINT`, `OUTPAINT`,
`NEGATIVE_PROMPT`, `CFG`, `LORA`, `CONTROLNET`, `REFERENCE_IMAGE`,
`IP_ADAPTER`, `CLIP_SKIP`, `CUSTOM_VAE`, `TRAINING`, `QUANTIZATION`,
`VIDEO`, `AUDIO`.

- No negative prompt support → hide the control.
- Distilled models with no meaningful CFG → hide CFG config.
- Controls that don't exist → not exposed.

### 3.4 ModelBundle

Model identity is a bundle, not an arbitrary path:

architecture, family, transformer/UNet, text encoders, tokenizers, VAE,
scheduler defaults, quantization, precision, supported tasks, license,
source, hashes, metadata, trigger words, recommended settings, minimum
memory.

Supported input formats: safetensors, Diffusers `model_index.json`,
`config.json`, `tokenizer.json`, SentencePiece, ONNX, external tensor
files, LoRA safetensors, ControlNet, textual inversion, custom VAEs,
quantized model metadata. PyTorch→ONNX conversion remains available but
is not the sole architecture.

### 3.5 ComputeBackend SPI

```java
public interface ComputeBackend {
    BackendDescriptor descriptor();
    boolean supports(Device device, ModelBundle model);
    BackendSession load(...);
    MemoryInfo memoryInfo();
    PerformanceCapabilities capabilities();
}
```

Backend matrix: ONNX Runtime (CPU/CoreML/CUDA/TensorRT/DirectML/
OpenVINO/ROCm where available), plus native Metal and CUDA backends for
future hot-path use. No premature native rewrite — profile first, replace
hot ops only where measured benefits justify it.

### 3.6 Schedulers & samplers

Reusable engine objects: Euler, Euler ancestral, DDIM, DPM++, Flow Match
Euler, LCM, architecture-specific distilled schedules. Capability
metadata specifies supported/recommended schedulers; incompatible
schedulers are not blindly exposed.

### 3.7 Memory manager

Deliberate memory management: estimation, controlled unloading,
component eviction, session lifetime, CPU↔GPU offload where appropriate,
sequential loading, VAE tiling, attention slicing, configurable memory
modes, OOM recovery, useful error reporting. `System.gc()` is never the
primary GPU memory mechanism.

---

## 4. Reproducibility

- Deterministic seeded generation per backend where realistically
  possible.
- Every output embeds a complete metadata manifest (JForge version, model
  ID/hash, VAE hash, LoRA/control hashes, prompt, negative prompt, seed,
  scheduler, steps, CFG, resolution, denoise strength, precision, backend,
  device, generation time, JVM version).
- Features: copy generation settings, recreate generation, export/import
  manifest.

---

## 5. Desktop product requirements

- **UI technology:** Compose Multiplatform Desktop / Kotlin JVM for the
  presentation layer; engine/API/models/backends/training/storage/
  CLI/server remain Java. If another JVM-native technology is proven
  materially better, document the decision.
- **New workspace:** tool rail + infinite canvas + contextual inspector +
  generation filmstrip (replaces Imagine/Enhance/Models card navigation).
- **Inspector** replaces giant form rows: compact contextual controls,
  collapsing sections, sliders + exact numeric editing.
- **Prompt experience:** multiline editor, history, autocomplete,
  presets, drag/drop reference, keyboard generate, token count where
  meaningful.
- **Live generation:** progress, step count, it/s, elapsed, ETA, device,
  backend, memory, cancel; low-cost intermediate previews where
  reasonable; no log-string staring.
- **Infinite canvas:** pan/zoom/trackpad/wheel, fit, 100%, selection,
  move/resize/rotate, crop, mask painting, drag/drop, copy/paste,
  multiple images, variants, references, before/after, undo/redo. Tiled
  rendering where large images would otherwise hurt responsiveness.
- **Layers/document model:** ImageLayer, GenerationLayer, MaskLayer,
  ReferenceLayer, GuideLayer, GroupLayer; non-destructive editing,
  variants, undo, history, masks, references; persisted `.jforge`
  projects (documented, versioned format).
- **Undo/redo:** all user-visible destructive operations participate.
- **Command palette** (Cmd/Ctrl+K), professional keyboard shortcuts,
  macOS trackpad support.
- **Design system:** spacing, radii, type scale, icons, surface
  hierarchy, hover/pressed/selected/disabled/focus/warning/error/success,
  tooltips, menus, dialogs. Near-black dark neutrals, restrained
  separators, compact controls, excellent typography, high information
  density. No giant cards, no web-dashboard aesthetics, no toy AI
  branding.
- **Animation:** 120–250ms purposeful motion; springs where appropriate;
  must never stall generation or canvas input; respect reduced-motion.
- **Developer console** behind View → Developer Console (logs, backend
  info, model loading, memory stats, exceptions, diagnostics).
- **Accessibility:** keyboard navigation, focus indication, screen-reader
  semantics, tab order, high contrast, reduced motion, scalable UI,
  tooltips, non-color-only status indicators — not deferred to the end.

---

## 6. Model experience

Visual model browser with filters (Image/Edit/Video/Fast/Low VRAM/LoRA/
Control/Trainable/Installed) and actions (Install/Use/Update/Show
files/Verify/Delete). Model cards answer: will it run on my hardware, how
much memory, download size, what it does, license, quantization choice —
**before** installation. Installation supports resume, checksum, disk
space, license metadata, dependency resolution, progress, cancellation,
retry, corrupt-download detection, clean uninstall, update detection,
duplicate detection.

Model sources: Hugging Face, local filesystem, manual import, URL import
(extension points reserved for CivitAI and similar).

---

## 7. Conditioning, LoRA, editing

- **LoRA:** import, validation, metadata, trigger words, adjustable
  strength, multiple simultaneous LoRAs, enable/disable, drag reorder,
  compatibility/model-family validation, saved combinations, preset
  import/export, per-block weighting where architecture permits.
- **Controls:** Canny/Depth/Pose/Scribble/Segmentation/Tile/Reference
  (IP-Adapter-style where supported); multiple simultaneous controls when
  the pipeline supports them; per-control strength, start/end percent,
  preprocessor, preview, enable toggle.
- **img2img:** real engine integration (drag onto canvas → source →
  denoise strength → variants → before/after).
- **Inpainting:** actual mask + source to engine; brush/eraser/size/
  softness/opacity/invert/clear/show/hide/feather/grow/shrink. No fake
  UI-only mask.
- **Outpainting:** integrated into the canvas (extend canvas → generate
  into exposed region), not a separate form.
- **Upscale/restore:** tile-based, large images, before/after, multiple
  upscalers, face restoration where appropriately licensed,
  denoise/sharpen, memory-aware; operates from canvas selection/history.

---

## 8. Training (LoRA)

Local LoRA training workspace: dataset folder, captioning, image
preprocessing, resolution, batch size, gradient accumulation, learning
rate, optimizer, epochs/steps, checkpointing, mixed precision, validation
prompts, training preview, resume, export. Reports loss/step/epoch/lr/
time/device/memory. Trained LoRAs immediately usable. Reliability
requirements: resume after interruption, safe checkpoints, dataset
validation, reject unsupported model/backend combos, protect against
overwrite, memory estimates before training.

---

## 9. Headless / automation

- **CLI:** `jforge model list`, `jforge generate --model ... --prompt ...
  --width ... --height ... --steps ... --seed ...`, `jforge upscale`,
  `jforge serve`, `jforge worker --listen`, `jforge benchmark`. Works
  without the desktop UI.
- **Java API:** clean embeddable API (`try (var forge = JForge.create())
  { ... result.image().save(...) }`); no Swing/Compose classes leak into
  engine API.
- **Server:** versioned REST (model/device listing, generation + status,
  cancel, image upload/retrieve, LoRA listing, worker status); optional
  gRPC. Protect endpoints when exposed beyond localhost.
- **Workers:** `jforge worker --listen`, discoverable from desktop;
  explicit and visible execution target (Local / Desktop RTX 3080 /
  Remote GPU server / Auto). Never silently send prompts/images remote.
- **Auto-scheduling:** choose target from model, VRAM, estimated memory,
  device speed, worker availability, network cost, user preference;
  manual override always.
- **Scripting:** safe JVM scripting (Java/Kotlin, JS via a safe JVM
  runtime, or declarative workflow API); no unrestricted filesystem/
  process access for untrusted scripts by default.
- **Plugin SDK:** versioned interfaces (PipelinePlugin, ModelSourcePlugin,
  ControlPlugin, PreprocessorPlugin, UpscalerPlugin, PostProcessorPlugin,
  ExportPlugin, ToolPlugin, MetadataPlugin) with ID/version/API-version/
  capabilities/permissions and safe failure isolation.
- **Workflow graph:** internal serializable graph (Prompt, Text Encoder,
  Model, LoRA, Control, Reference, Sampler, VAE, Upscale, Image
  Processor, Export); advanced users may edit; normal users need not see it.
- **ComfyUI interop:** import a supported workflow subset, export JForge
  workflows, identify unsupported nodes, map models/LoRAs/samplers/
  controls. Report compatibility honestly; never overclaim.

---

## 10. Milestones (target scores in `TRUTH.md`)

| Milestone | Target | Focus |
|---|---|---|
| M0 Engine correctness | 30 | truthful audit, typed request, batch/steps fix, pipeline+scheduler+backend abstractions, split god object, deterministic seeds, golden tests, error handling |
| M1 Product shell | 40 | modern UI, design system, workspace, inspector, palette, filmstrip, model browser, live status, dev console |
| M2 Modern model engine | 52 | model bundles, safetensors, Diffusers ingestion, modern architectures, capability UI, memory-aware execution |
| M3 Creative canvas | 65 | infinite canvas, project model, layers, undo/redo, img2img, masks, inpaint, outpaint, before/after |
| M4 Conditioning | 75 | LoRA, multi-LoRA, ControlNet, preprocessors, reference (IP-Adapter), external VAE, textual inversion |
| M5 Performance & training | 84 | quantization, memory modes, benchmarking, LoRA training, checkpoint/resume, profiled backend optimizations |
| M6 Platform | 90 | CLI, Java API, REST server, workers, plugin API, scripting, workflow serialization, ComfyUI interop |
| M7 Media | 95 | video generation, image→video, timeline/preview, encoding/export, restoration |
| M8 Production | 100 | installers, runtime bundling, crash recovery, accessibility, visual QA, hardware validation, security/performance audits, packaging, signed-release prep, docs, zero P0/P1 |

Exit gates are defined per milestone in the source-of-truth goal
(`PROMPT.md` contents mirrored in repo history). Build continuously:
every substantial tranche runs `mvn test` and packaging jobs. Do not
stack changes on a broken build.

---

## 11. Engineering rules

- Small focused classes; clear interfaces; immutable configuration;
  explicit ownership/lifetimes; no giant utility classes; no duplicated
  model logic; no magic model-name checks; no swallowed exceptions
  without justification; no uncontrolled global mutable state; no
  UI-thread inference; no placeholder production code.
- Do not break working features. Establish tests for existing behavior
  before destructive refactors. Migrate incrementally.
- No fake features, no inflated scores (see `TRUTH.md` vocabulary).
- Strict no-fake-feature policy: a feature needs implementation **and**
  validated behavior.
- When choosing between a quick hack and an architecture that safely
  supports the next 20 model families, prefer the latter without
  speculative overengineering. When choosing between a feature checkbox
  and a complete usable workflow, choose the workflow. Polish and
  functionality are both required.

---

## 12. QA gates

### 12.1 Testing

Categories: unit, integration, golden inference, model parser, scheduler,
tokenizer, storage, download, checksum, UI state, project persistence,
CLI, server API, plugin API, workflow serialization, backend capability,
failure recovery.

Golden inference fixtures per supported architecture: prompt, negative
prompt, seed, steps, scheduler, CFG, resolution, model hash, expected
output characteristics. Cross-backend comparisons use tolerant checks
(perceptual similarity, tensor tolerances, structural checks);
determinism validated on the same backend configuration where feasible.

CI must include small real inference smoke tests when practical (tiny
test models, mocked layers, scheduled heavier validation, release-time
hardware tests) — not tens of gigabytes per CI run.

### 12.2 UI QA

Visual QA: build → run → screenshot → inspect → compare to goals → fix →
repeat. Interaction QA: mouse, keyboard, trackpad, resize, fullscreen,
HiDPI, dark/light, multi-monitor, drag/drop, undo/redo, cancellation of
generation and downloads. Performance QA: startup, idle RAM, canvas FPS,
zoom latency, large-image canvas, generation progress responsiveness,
model-switch time, model-manager scrolling, hundreds/thousands of
history entries; target 60 FPS UI on ordinary hardware, inference never
freezes the UI thread.

### 12.3 Failure handling

Explicitly tested: missing/corrupt/unsupported model, bad tokenizer,
missing VAE, OOM, GPU driver problem, provider init failure, disk full,
download interruption, network interruption, permission failure, invalid
project, remote worker disconnect, cancel during generation/download/
training. Errors are understandable and actionable; no stack traces in
the normal UI.

### 12.4 Hardware quality matrix

Verified results for NVIDIA RTX 20/30/40/50, AMD representative RDNA,
Intel representative Arc, Apple representative M-series. Where physical
hardware is unavailable, use CI/cloud/community validation and label
"unverified" rather than pretending.

### 12.5 Release gates

A release candidate requires: all builds pass, unit + integration tests
pass, golden suite passes, UI visual QA passes, major workflows manually
smoke-tested, zero P0/P1 defects, install packages produced, fresh-machine
install tested, documentation matches reality, `TRUTH.md >= 98/100`.
100/100 additionally requires closure of remaining production-quality gaps.

### 12.6 Packaging & release

Real desktop packages: Windows installer, macOS `.app`/distributable,
Linux AppImage and/or native packages, via `jpackage` or equivalent;
bundle JVM runtime where feasible; users never install Maven. Prepare
macOS/Windows signing infrastructure; automate everything except
secret-dependent signing and document it honestly. Secure update path
(no unsigned binaries). Security audit: model downloads, archive
extraction, plugin loading, script execution, remote server/worker, path
traversal, malformed metadata, unsafe deserialization, temp files, token
storage. Credentials stored via platform facilities; secrets never
logged.

---

## 13. Observability & privacy

Structured logging with generation ID, model ID, backend, device, phase,
duration, memory. Separate user-facing status from developer diagnostics.
Default behavior: local inference/history/prompts/projects. Remote-worker
execution clearly identifies where data is sent. Local-only performance
telemetry (load time, first generation latency, steps/sec, peak VRAM/RAM,
VAE decode time, model-switch time). No external telemetry unless opt-in.

---

## 14. Documentation

Architecture, model integration, backend API, pipeline API, plugin API,
workflow format, CLI, server, building, testing, packaging, benchmarking,
supported hardware, supported models, known limitations — concise and
truthful, in `README.md` and repo docs.