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

## Current score (re-scored 2026-08-25 — M1 inspector + model browser tranche)

**Total: 35 / 100**

| Category | Weight | Score | Notes |
|---|---|---|---|
| Inference architecture & correctness | 15 | 8 | God object split into per-architecture pipelines over shared engine components; typed request; batch/steps decoupled; deterministic seeds locked by test; scheduler math pinned by hand-computed golden values; first full `mvn test` is green (67 tests). Legacy adapter still advisory on scheduler + sequential batch |
| Model-family coverage | 10 | 2 | SD 1.5 + SDXL + SD 3.x (converted); no FLUX/Qwen/Z-Image, no real model bundle abstraction |
| Image generation quality/features | 10 | 4 | Real t2i works; no img2img, no real inpaint/outpaint engine support |
| Canvas/editing/inpaint/outpaint | 10 | 1 | Placeholder infinite canvas with pan/zoom/checkerboard (CanvasPanel) inside new workspace shell; no document model/layers/undo yet |
| LoRA/Control/reference conditioning | 10 | 0 | None |
| GPU backends/performance/memory | 10 | 4 | ONNX Runtime EP probing real; CoreML `System.gc()` hack removed; still no benchmark harness or memory estimation |
| UI/UX/product polish | 15 | 9 | DesignTokens + WorkspaceShell (tool rail/canvas/inspector/filmstrip); InspectorController with capability-driven collapsible sections (prompt history/token count, CFG/neg hidden per pipeline); live GenerationStatusBar; polished ModelManager with filters (Image/Edit/Video/Fast/LowVRAM/Installed) + actions (Use/Show/Verify/Delete); Command Palette (Cmd/Ctrl+K); Developer Console |
| Training/model tooling | 5 | 1 | PyTorch→ONNX conversion is real; no LoRA training |
| Video/media workflows | 5 | 0 | None |
| CLI/API/server/plugins/workflows | 5 | 2 | `JForge` embeddable Java API + `jforge model list` / `jforge generate` CLI are real; no server/plugins/workers |
| QA/reliability/release/accessibility | 5 | 4 | 85 tests green locally (adds inspector tests + workspace tests); CI `test` + `test-windows` jobs configured but not yet executed; no macOS CI, no UI/visual QA |

### How this re-score was established

- Baseline was audited statically at commit `ca6732b` (22/100) — local box
  had no JDK 21 / Maven, so `mvn test` could not run then.
- This re-score follows the full M0 tranche on branch
  `milestone-m0-engine-correctness`: abstractions, god-object split,
  deterministic seeds, and golden tests, validated by a real
  `mvn -B test` run (portable Temurin JDK 21.0.12 + Maven 3.9.9),
  **BUILD SUCCESS, 67 tests, 0 failures**.
- The first ever full test run surfaced latent branch breakage that the
  never-run suite had hidden: a missing import in `GenerationPipeline`
  (`engine.backend.ComputeBackend`), a non-effectively-final var in a
  `LegacyInferencePipeline` lambda, a test-expected
  `GenerationManifest.emptyManifest()` factory that did not exist, an
  unhandled `IOException` in a test lambda, wrong shape assertions in
  two scheduler tests, and two real behavior gaps (default
  `supportsScheduler(DDIM)` was false despite the DDIM fallback contract;
  `GenerationManifest.toRequest()` dropped the recorded backend/device so
  "recreate generation" could not reproduce the execution target). All
  fixed; the failing tests encode the intended contract and now pass.
- 2026-08-25 M1 tranche: design system + workspace shell + command palette + dev console landed; UI/UX 3→7, canvas 0→1, QA 3→4, total 27→33 (first visible workspace anatomy; M1 target 40 not yet reached — inspector still hosts legacy sidebar, no document/layers/undo, no full design-system coverage, no Compose migration yet).
- Score remains below the M0 target of 30 because M1 (product shell) is
  untouched and inference accuracy itself is not yet model-level verified. [Now superseded: M1 has started, but M0's model-level golden verification is still pending.]

---

## Milestone ledger

### M0 — Engine correctness (target 30)

Progress:

- [x] Typed `GenerationRequest` with separate steps / batchSize — `jforge-api` (new `api` package: `GenerationRequest`, builder, `GenerationOptions`, `ImageInput`, `ImageMask`, `ControlInput`, `ReferenceImage`, `LoRAConfig`, `Precision`, `Quantization`, `SchedulerType`) + `GenerationRequestTest`
- [x] Pipeline abstraction (`GenerationPipeline` SPI) — `engine` package: `GenerationPipeline`, `LoadedPipeline`, `PipelineDescriptor`, `LoadOptions`, `PipelineCapabilities` + `Capability` enum (UI generated from capabilities) + `PipelineCapabilitiesTest`
- [x] Scheduler abstraction (separate reusable scheduler objects) — `engine.scheduler`: `Scheduler` SPI, `FloatLatents`, `SchedulerMath`, `DdimScheduler`, `EulerScheduler`, `FlowMatchEulerScheduler`, `DistilledEulerScheduler` + `SchedulerMathTest`, `SchedulerTest`
- [x] Backend abstraction (`ComputeBackend` SPI) — `engine.backend`: `ComputeBackend`, `BackendSession`, `BackendDescriptor`, `Device`, `DeviceKind`, `MemoryInfo`, `PerformanceCapabilities`; real impl `engine.legacy.OnnxRuntimeBackend` driven by `GenericOnnxService.detectedProvider()`
- [x] New engine abstractions drive the legacy engine — `engine.legacy.RequestMapper` (typed `GenerationRequest` → legacy `InferenceRequest`, fixing the historical batch/steps conflation: `steps` now maps into the legacy "batch" slot that every pipeline reads as its step count, verified by `RequestMapperTest`); `LegacyInferencePipeline` (a real `GenerationPipeline` over `InferenceService`); `api.JForge` embeddable facade (`try (var forge = JForge.create())`); `cli.JForgeCli` (`jforge model list`, `jforge generate ...`)
- [x] `GenericOnnxService` split into per-architecture pipelines — CLIP + T5 tokenizers extracted to public reusable `tokenize` package; scheduler/latent math delegated to shared `engine.scheduler.SchedulerMath` and `engine.random.LatentNoise`; SD3 shifted-sigma loop uses `FlowMatchEulerScheduler.shifted()`. The god object is now a thin router (`GenericOnnxService` dispatches by model id) over `inference.OnnxPipelineBase` (shared session/tokenizer caches, text encoders, tensor extraction, image conversion) and six per-architecture pipelines: `Sd15OnnxPipeline`, `SdTurboOnnxPipeline`, `SdxlTurboOnnxPipeline`, `SdxlBaseOnnxPipeline`, `Sd3OnnxPipeline`, `RealEsrganOnnxPipeline`. Method bodies were moved verbatim; run-method dispatch behavior unchanged. Public surface (`GenericOnnxService(TaskType, ModelStorage, Executor)`, `run`, `detectedProvider()`, `clearCache()`) preserved — verified by grep against all callers (`ServiceFactory`, `JForge`, `OnnxRuntimeBackend`, `MainFrame`, `GenericOnnxServiceTest`).
- [x] Deterministic seeded latents verified by test — `engine.random.LatentNoise` + `LatentNoiseTest` (same seed → identical noise, different seeds differ); `RANDOM_SEED` marker preserved through `build()` and resolved once at the pipeline boundary so `isRandomSeed()` is truthful and the resolved seed is stamped into the manifest
- [x] Golden inference tests (at least scheduler/tokenizer level) — `ClipTokenizerTest`, `T5TokenizerTest` with real fixture files; `SchedulerGoldenTest` pins hand-computed expected values (alpha_cumprod, timestep strides, turbo sigma profile, flow-match shifted sigmas, DDIM/Euler single-step outputs) so formula changes fail loudly
- [x] Structured error handling without stack traces in UI — `GenerationResult.ok/fail` + manifest
- [x] Regression tests before destructive refactors — tokenizer extraction covered by identical-code regression expectation + `GenericOnnxServiceTest`
- [x] First full `mvn test` green on branch — portable JDK 21.0.12 + Maven 3.9.9: **67 tests, 0 failures** (`mvn -B test -Dort.artifactId=onnxruntime`). Surfaced and fixed latent build/test/behaviour breaks hidden by the never-run suite (see re-score notes); CoreML `System.gc()` step-hack removed from `Sd15OnnxPipeline`
- [x] Headless CLI extended + documented honestly — `jforge upscale --model realesrgan --image <path>` routes an input image through the typed API (`JForgeCli.upscaleRequest`); README no longer claims CLI/embeddable-API are "not yet built", documents real flags and the `JForge.create()` snippet; suite now **70 tests, 0 failures**
- [x] Honest bundle derivation + actionable unknown-model errors — `JForge.generate` resolves the registry descriptor first; unknown ids fail with a message naming the id and pointing at `jforge model list` (was the misleading "model must not be null"); bundles now derive family/displayName/componentRoot/source from what the registry actually knows instead of stamping a fake `"stable-diffusion-1.x"` architecture on every request. Suite **71 tests, 0 failures**; Windows CI test job added (`test-windows`)

Blockers:

- None technical. Engine is small enough to refactor incrementally.
- CI is not auto-triggered for feature branches (workflow only runs on `main` push + PRs). The `milestone-m0-engine-correctness` branch is pushed; a PR to `main` is required to exercise the `test` job in CI.
- Legacy adapter limitations (documented in `LegacyInferencePipeline`): legacy selects schedulers internally by model id so `request.scheduler()` is advisory; `batchSize > 1` runs as sequential derived-seed runs, not true batched inference.

### M1 — Product shell (target 40)

Progress:

- [x] Design system foundation — `ui.design.DesignTokens` (near-black dark neutrals, spacing 4–32, radii 4–12, type scale 10–13pt, surface hierarchy, accent/success/warning/error, selection/hover states) + `ComponentStyles`; near-black neutrals, restrained separators, compact controls, high density
- [x] Workspace anatomy — `ui.workspace.WorkspaceShell` (tool rail 48px + canvas + inspector 300px + filmstrip 112px) replaces the Imagine/Enhance/Models card navigation visually; `ToolRail`, `CanvasPanel` (checkerboard, pan via drag, zoom via wheel, fit/100%), `InspectorPanel` (section cards, scroll), `FilmstripPanel` (horizontal strip), `GenerationStatusBar`; MainFrame now assembles the workspace shell while preserving lazy card panels inside the center (no workflow breakage)
- [x] Command palette — `ui.palette.CommandPalette` (Cmd/Ctrl+K, filterable list, keyboard navigation, 8 commands: view switches, dark mode, dev console, canvas fit/zoom)
- [x] Developer console — `ui.console.DeveloperConsole` behind View → Developer Console (logs, backend/device/memory/model info, clear)
- [x] Inspector — capability-driven collapsing sections (`ui.inspector.InspectorController` with `CollapsibleSection`, `SliderField`, `PromptEditor` with token count/history/autocomplete, prompt, negative prompt, steps, CFG, seed, scheduler, resolution); `bindCapabilities` hides CFG/negative prompt when pipeline lacks them (tested by `InspectorControllerTest` — suite **85 tests**)
- [x] Live generation status — `GenerationStatusBar` (progress, steps, it/s, ETA, device, backend, memory, cancel) wired into `MainFrame` south stack above status bar (idle/progress states; full inference wiring pending pipeline progress callbacks)
- [x] Model browser polish — `ModelManagerPanel` now has filters (Image/Edit/Video/Fast/Low VRAM/Installed + free-text search, live count) and actions (Use, Show files, Verify, Delete); `ModelTableModel` supports predicate filtering (`setFilter`) and filtered `Available`/`Progress` display
- [x] Workspace unit tests — `DesignTokensTest` (scale monotonicity, dimensions, colors/fonts non-null), `WorkspaceShellTest` (shell regions, tool selection, zoom clamping, inspector/filmstrip content), `InspectorControllerTest` (prompt/history, slider sync, CFG visibility) — suite **85 tests, 0 failures**

Blockers:

- Canvas is a placeholder checkerboard, not the infinite canvas with layers/document model/undo/tiles/selection/masks
- No animation (120–250ms springs) yet; motion must respect reduced-motion
- Compose Multiplatform migration not started — current shell is Swing/FlatLaf; acceptable per migration rule (package boundaries first), but the product contract requires Compose for the presentation layer — decision to stay on Swing for this tranche is documented here as incremental

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
| Deterministic seeds | implemented | `Random(seed)` for latents; `seeded` marker resolved once at pipeline boundary and logged; locked by `LatentNoiseTest` |
| Negative prompt / CFG | implemented | SD 1.5, SDXL Base, SD3 |
| Prompt weighting | implemented | `promptWeight` used as CFG in SDXL/SD3; ignored by distilled paths |

### Model management

| Feature | Status | Evidence |
|---|---|---|
| HF discovery & download w/ resume | implemented | `ModelDownloader` |
| PyTorch→ONNX conversion | implemented | `PyTorchToOnnxConverter` + Python scripts |
| Gated model token auth | implemented | `ModelDownloader` |
| Model bundle abstraction | partial | typed `engine.ModelBundle` record + builder; no safetensors/Diffusers ingestion yet |
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
| Swing main frame | implemented | `MainFrame` (now workspace anatomy via `WorkspaceShell`) |
| Design system | partial | `ui.design.DesignTokens` + `ComponentStyles` (near-black neutrals, spacing/radii/type, surface hierarchy) |
| Workspace shell | partial | `ui.workspace.WorkspaceShell` + `ToolRail` + `CanvasPanel` + `InspectorPanel` + `FilmstripPanel` + `GenerationStatusBar` |
| Text-to-image form | implemented | `TextToImagePanel` (still card-based; inspector migration pending) |
| Upscale form | implemented | `ImageUpscalePanel` |
| Model manager | implemented | `ModelManagerPanel` with filters (Image/Edit/Video/Fast/LowVRAM/Installed, search, count) + actions (Use/Show files/Verify/Delete) + progress |
| History gallery | implemented | `HistoryPanel` (filmstrip placeholder added) |
| Inspector | partial | `ui.inspector.InspectorController` (prompt/history/token count, collapsible Generation/Canvas sections, capability-driven CFG/neg visibility) |
| Live generation status | partial | `GenerationStatusBar` (steps/it/s/ETA/device/backend/memory/cancel) wired into frame; progress plumbing to pipeline pending |
| Infinite canvas | partial | `CanvasPanel` checkerboard with pan/zoom/fit; no layers/document/undo/tiles yet |
| Command palette | implemented | `ui.palette.CommandPalette` (Cmd/Ctrl+K, View menu) |
| Developer console | implemented | `ui.console.DeveloperConsole` (View → Developer Console, logs/backend/memory) |

### Platform

| Feature | Status | Evidence |
|---|---|---|
| CLI (`jforge ...`) | partial | `cli.JForgeCli`: `jforge model list`, `jforge generate`, `jforge upscale` (serve/worker/benchmark subcommands pending) |
| Java embeddable API | implemented | `api.JForge` facade (`try (var forge = JForge.create()) { ... }`) |
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
| Unit — schedulers | implemented | `SchedulerMathTest`, `SchedulerTest`, `SchedulerGoldenTest` (hand-computed golden values) |
| Unit — tokenizers | implemented | `ClipTokenizerTest`, `T5TokenizerTest` with fixture files |
| Unit — API / engine | implemented | `GenerationRequestTest`, `PipelineCapabilitiesTest`, `GenerationManifestTest`, `LegacyInferencePipelineTest`, `LatentNoiseTest`, `RequestMapperTest` |
| Unit — design/workspace | implemented | `DesignTokensTest` (5), `WorkspaceShellTest` (4) |
| Golden inference | partial | tokenizer/scheduler-level golden values; full model-level golden outputs pending real model fixtures |
| Integration | partial | `LegacyInferencePipelineTest` exercises the typed→legacy bridge with a stubbed `InferenceService` |
| CI — Linux build | implemented | `.github/workflows/build.yml` (packaging uses `-DskipTests`) |
| CI — Windows | implemented | `test-windows` job added (windows-latest, JDK 21) |
| CI — macOS | planned | — |
| CI — tests run | configured (not yet executed) | `test` + `test-windows` jobs added; branch not yet CI-validated (workflow needs `main` push or PR) |

---

## Known blockers / risks

1. **CI test jobs not yet executed.** The `test` (Linux) and
   `test-windows` jobs exist in the workflow but have not run — the M0
   branch needs a PR to `main` to trigger them. Local `mvn test` is
   green (71 tests).
2. **Single-module build.** Acceptable interim per migration rule, but
   module split is pending.
3. **Legacy adapter fidelity.** Legacy pipelines pick schedulers
   internally by model id and run `batchSize > 1` as sequential
   derived-seed runs, not true batched inference.
4. **Model-level inference not golden-verified.** Scheduler/tokenizer
   math is pinned by tests, but no runnable small-model fixture yet
   locks full pipeline outputs.

---

## Score rules

- Never raise a score without evidence.
- A refactor that preserves behavior does not by itself raise the score;
  it only unlocks future work.
- Scores rise when new *validated* capability appears.
- When context runs low: update this file, commit a coherent checkpoint,
  continue from the highest-priority gap.
