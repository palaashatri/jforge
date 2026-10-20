package atri.palaash.jforge.inference;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.engine.*;

public final class FluxPipeline implements GenerationPipeline {
    @Override public PipelineDescriptor descriptor() { return new PipelineDescriptor("flux", "FLUX.1", "flux", 1); }
    @Override public PipelineCapabilities capabilities() {
        return PipelineCapabilities.builder().capabilities(Capability.TEXT_TO_IMAGE, Capability.CFG).scheduler(atri.palaash.jforge.api.SchedulerType.FLOW_MATCH_EULER).build();
    }
    private static final class Loaded implements LoadedPipeline {
        final ModelBundle model;
        Loaded(ModelBundle m) { this.model = m; }
        @Override public PipelineDescriptor descriptor() { return new PipelineDescriptor("flux", "FLUX.1", "flux", 1); }
        @Override public void close() {}
    }
    @Override public LoadedPipeline load(ModelBundle model, atri.palaash.jforge.engine.backend.ComputeBackend backend, LoadOptions opts) throws Exception {
        if (!model.family().toLowerCase().contains("flux")) throw new IllegalArgumentException("Not a FLUX bundle: " + model.family());
        return new Loaded(model);
    }
    @Override public GenerationResult generate(LoadedPipeline p, GenerationRequest req, ProgressListener prog, CancellationToken cancel) {
        if (cancel.isCancelled()) return GenerationResult.fail(req.modelId(), "Cancelled");
        prog.onProgress(new GenerationProgress("flux", 1, 1, -1, 0, -1, "", "", -1, false));
        String mid = p instanceof Loaded l ? l.model.id() : req.modelId();
        return GenerationResult.fail(req.modelId(), "FLUX pipeline validated bundle " + mid + " — execution wiring pending");
    }
}
