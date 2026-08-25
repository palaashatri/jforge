package atri.palaash.jforge.inference;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.engine.*;

public final class VideoDiffusionPipeline implements GenerationPipeline {
    @Override public PipelineDescriptor descriptor() { return new PipelineDescriptor("video-diffusion", "Video Diffusion", "video", 1); }
    @Override public PipelineCapabilities capabilities() {
        return PipelineCapabilities.builder().capabilities(Capability.TEXT_TO_IMAGE, Capability.VIDEO).build();
    }
    private static final class Loaded implements LoadedPipeline {
        final ModelBundle model;
        Loaded(ModelBundle m) { this.model = m; }
        @Override public PipelineDescriptor descriptor() { return new PipelineDescriptor("video-diffusion", "Video Diffusion", "video", 1); }
        @Override public void close() {}
    }
    @Override public LoadedPipeline load(ModelBundle model, atri.palaash.jforge.engine.backend.ComputeBackend backend, LoadOptions opts) throws Exception {
        return new Loaded(model);
    }
    @Override public GenerationResult generate(LoadedPipeline p, GenerationRequest req, ProgressListener prog, CancellationToken cancel) {
        return GenerationResult.fail(req.modelId(), "Video pipeline validated — video encoding not yet wired");
    }
}
