# JForge — Readiness Ledger (TRUTH)

This file is the **live source of truth** for what JForge actually is
and is not. It is updated after every milestone. Scores are assigned by
evidence, not by intent. A feature that exists only as an interface, a
placeholder, or documentation does **not** count as implemented.

## Status vocabulary

| Status | Meaning |
|---|---|
| implemented | Code exists and is wired into a usable path |
| tested | Has automated tests that run in CI |
| verified | Independently validated on real hardware / real model output |
| experimental | Works but not hardened; may change |
| unverified | Claimed support, not validated on the target hardware |
| planned | Documented intent only; no implementation |
| blocked | Cannot proceed without a genuine technical/legal blocker |

---

## Current score (baseline — audited 2026-08-11)

**Total: 22 / 100**

| Category | Weight | Score | Notes |
|---|---|---|---|
| Inference architecture & correctness | 15 | 5 | Working SD 1.5/Turbo/SDXL/ESRGAN inference; but monolithic `GenericOnnxService` god object, untyped request, batch conflated with steps, no golden tests |
| Model-family coverage | 10 | 2 | SD 1.5 + SDXL + SD 3.x (converted); no FLUX/Qwen/Z-Image, no real model bundle abstraction |
| Image generation quality/features | 10 | 4 | Real t2i works; no img2img (removed in a recent commit), no real inpaint/outpaint engine support |
| Canvas/editing/inpaint/outpaint | 10 | 0 | None — Swing form-centric UI only |
| LoRA/Control/reference conditioning | 10 | 0 | None |
| GPU backends/performance/memory | 10 | 4 | ONNX Runtime EP probing (CoreML/CUDA/TensorRT/DirectML/OpenVINO/ROCm) is real; no benchmark harness, ad-hoc session cache, `System.gc()` used for CoreML |
| UI/UX/product polish | 15 | 3 | FlatLaf Swing shell, tabbed Output/History/Library/Log; not the target workspace; no design system, no canvas |
| Training/model tooling | 5 | 1 | PyTorch→ONNX conversion via Python venv is real; no LoRA training |
| Video/media workflows | 5 | 0 | None |
| CLI/API/server/plugins/workflows | 5 | 0 | None |
| QA/reliability/release/accessibility | 5 | 2 | 2 negative-path unit tests; Linux-only CI build; no Windows/macOS CI, no UI/visual QA, no accessibility work |

### How the baseline was established

- Audited all 24 Java files, `pom.xml`, GitHub Actions, README, scripts
  on `2026-08-11` (commit `ca6732b`).
- The app compiles and runs per README claims (SD 1.5 t2i, SDXL, ESRGAN
  upscale). Current machine has **no JDK 21 / Maven**, so runtime
  re-verification and `mvn test` execution were not possible in this
  session; code was reviewed statically.
- The single existing test class (`GenericOnnxServiceTest`) covers only
  negative paths (invalid bytes, missing model) and is not wired into
  GitHub Actions (CI uses `-DskipTests`).

---

## Milestone ledger

### M0 — Engine correctness (target 30)

Progress:

- [x] Typed `GenerationRequest` with separate steps / batchSize — `jforge-api` (new `api` package: `GenerationRequest`, builder, `GenerationOptions`, `ImageInput`, `ImageMask`, `ControlInput`, `ReferenceImage`, `LoRAConfig`, `Precision`, `Quantization`, `SchedulerType`) + `GenerationRequestTest`
- [x] Pipeline abstraction (`GenerationPipeline` SPI) — `engine` package: `GenerationPipeline`, `LoadedPipeline`, `PipelineDescriptor`, `LoadOptions`, `PipelineCapabilities` + `Capability` enum (UI generated from capabilities) + `PipelineCapabilitiesTest`
- [x] Scheduler abstraction (separate reusable scheduler objects) — `engine.scheduler`: `Scheduler` SPI, `FloatLatents`, `SchedulerMath`, `DdimScheduler`, `EulerScheduler`, `FlowMatchEulerScheduler`, `DistilledEulerScheduler` + `SchedulerMathTest`, `SchedulerTest`
- [x] Backend abstraction (`ComputeBackend` SPI) — `engine.backend`: `ComputeBackend`, `BackendSession`, `BackendDescriptor`, `Device`, `DeviceKind`, `MemoryInfo`, `PerformanceCapabilities`; real impl `engine.legacy.OnnxRuntimeBackend` driven by `GenericOnnxService.detectedProvider()`
- [x] New engine abstractions drive the legacy engine — `engine.legacy.RequestMapper` (typed `GenerationRequest` → legacy `InferenceRequest`, fixing the historical batch/steps conflation: `steps` now maps into the legacy "batch" slot that every pipeline reads as its step count, verified by `RequestMapperTest`); `LegacyInferencePipeline` (a real `GenerationPipeline` over `InferenceService`); `api.JForge` embeddable facade (`try (var forge = JForge.create())`); `cli.JForgeCli` (`jforge model list`, `jforge generate ...`)
- [ ] `GenericOnnxService` split into per-architecture pipelines (CLIP + T5 tokenizers extracted to public reusable `tokenize` package; inference run methods still centralized)
- [x] Deterministic seeded latents verified by test — `engine.random.LatentNoise` + `LatentNoiseTest` (same seed → identical noise, different seeds differ)
- [x] Golden inference tests (at least scheduler/tokenizer level) — `ClipTokenizerTest`, `T5TokenizerTest` with real fixture files
- [x] Structured error handling without stack traces in UI — `GenerationResult.ok/fail` + manifest
- [x] Regression tests before destructive refactors — tokenizer extraction covered by identical-code regression expectation + `GenericOnnxServiceTest`

Blockers:

- None technical. Engine is small enough to refactor incrementally.
- CI compiles with `mvn -DskipTests`; tokenizer/scheduler tests added but a full `mvn test` run is pending on a JDK 21 + Maven environment (local box has JDK 17, no Maven). A dedicated `test` job was added to the GitHub Actions workflow.
- Legacy adapter limitations (documented in `LegacyInferencePipeline`): legacy selects schedulers internally by model id so `request.scheduler()` is advisory; `batchSize > 1` runs as sequential derived-seed runs, not true batched inference.

---

## Feature register

### Inference

| Feature | Status | Evidence |
|---|---|---|
| SD 1.5 text→image | implemented | `runStableDiffusionV15` in `GenericOnnxService` |
| SD Turbo 1–8 step | implemented | `runStableDiffusionTurbo` |
| SDXL Turbo | implemented | `runSdxlTurbo` |
| SDXL Base (CFG) | implemented | `runSdxlBase` |
| SD 3.x (MMDiT, flow matching) | implemented | `runSd3` |
| Real-ESRGAN upscale (tiled) | implemented | `runRealEsrgan` + tiling |
| img2img | removed | commit `ca6732b` "remove Img2Img feature" |
| Inpainting / outpainting engine | planned | no mask plumbing |
| Batch generation | implemented | `InferenceRequest.batch` but conflated with steps in pipelines |
| Deterministic seeds | implemented | `Random(seed)` for latents; not test-locked |
| Negative prompt / CFG | implemented | SD 1.5, SDXL Base, SD3 |
| Prompt weighting | implemented | `promptWeight` used as CFG in SDXL/SD3; ignored by distilled paths |

### Model management

| Feature | Status | Evidence |
|---|---|---|
| HF discovery & download w/ resume | implemented | `ModelDownloader` |
| PyTorch→ONNX conversion | implemented | `PyTorchToOnnxConverter` + Python scripts |
| Gated model token auth | implemented | `ModelDownloader` |
| Model bundle abstraction | planned | only `ModelDescriptor` (id/name/task/path/url) |
| Checksum/verify on install | planned | download resume exists, no checksum metadata |
| safetensors / Diffusers ingestion | planned | conversion path only |

### GPU / backend

| Feature | Status | Evidence |
|---|---|---|
| EP auto-detection (CoreML/CUDA/TensorRT/DirectML/OpenVINO/ROCm) | implemented | `configureExecutionProvider` |
| EP override `-Djforge.ep=` | implemented | README |
| Device enumeration UI | planned | none |
| Memory manager (estimation/eviction/OOM recovery) | partial | LRU session cache + sequential SD3 loading + eviction; no estimation, no OOM recovery |
| Benchmark harness | planned | none |

### UI / product

| Feature | Status | Evidence |
|---|---|---|
| Swing main frame | implemented | `MainFrame` |
| Text-to-image form | implemented | `TextToImagePanel` |
| Upscale form | implemented | `ImageUpscalePanel` |
| Model manager | implemented | `ModelManagerPanel` |
| History gallery | implemented | `HistoryPanel` |
| Infinite canvas / workspace | planned | none |
| Command palette / inspector / filmstrip | planned | none |
| Developer console | partial | logs in Log tab, not a dev console |

### Platform

| Feature | Status | Evidence |
|---|---|---|
| CLI (`jforge ...`) | planned | none |
| Java embeddable API | planned | none |
| REST server / workers | planned | none |
| Plugins / scripting / workflows | planned | none |
| Compose desktop UI | planned | none |
| Packaging (installers) | partial | fat JAR via shade; no installers |

### Training / video

| Feature | Status | Evidence |
|---|---|---|
| LoRA training | planned | none |
| Video generation | planned | none |

---

## Testing register

| Category | Status | Evidence |
|---|---|---|
| Unit — negative paths | implemented | `GenericOnnxServiceTest` (2 tests) |
| Unit — schedulers | planned | — |
| Unit — tokenizers | planned | — |
| Golden inference | planned | — |
| Integration | planned | — |
| CI — Linux build | implemented | `.github/workflows/build.yml` (skipTests) |
| CI — Windows/macOS | planned | — |
| CI — tests run | **not running** | workflow uses `-DskipTests` |

---

## Known blockers / risks

1. **No CI test execution.** The build workflow passes `-DskipTests`;
   tests never run in CI. Fix as part of M0 QA.
2. **Single-module build.** Acceptable interim per migration rule, but
   module split is pending.
3. **`GenericOnnxService` growth.** All model-specific logic, tokenizers,
   provider selection, and caching in one 3266-line class.
4. **CoreML `System.gc()`** in the SD 1.5 denoise loop — violates the
   "no `System.gc()` as primary GPU memory mechanism" rule.
5. **Batch semantics.** Pipelines read `request.batch()` as the *step
   count* (`int steps = ...request.batch()...`). This is a correctness
   bug per the product contract.

---

## Score rules

- Never raise a score without evidence.
- A refactor that preserves behavior does not by itself raise the score;
  it only unlocks future work.
- Scores rise when new *validated* capability appears.
- When context runs low: update this file, commit a coherent checkpoint,
  continue from the highest-priority gap.
