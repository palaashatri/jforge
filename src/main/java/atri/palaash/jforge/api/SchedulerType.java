package atri.palaash.jforge.api;

/**
 * Supported sampling / stepping strategies for denoising.
 * <p>
 * A scheduler is the ODE/SDE integrator that walks the noisy latent
 * back toward a clean sample.  Schedulers are independent reusable
 * engine objects; pipelines must declare which schedulers they support
 * via {@code PipelineCapabilities}.
 */
public enum SchedulerType {
    /** Euler method on the sigma schedule. */
    EULER("Euler"),
    /** Euler method with ancestral (stochastic) noise injection. */
    EULER_ANCESTRAL("Euler ancestral"),
    /** Denoising Diffusion Implicit Models. */
    DDIM("DDIM"),
    /** DPM-Solver++ (2nd order). */
    DPM_PLUS_PLUS("DPM++"),
    /** Flow-matching Euler used by SD3 / FLUX style models. */
    FLOW_MATCH_EULER("Flow Match Euler"),
    /** Latent Consistency Models distilled schedule. */
    LCM("LCM"),
    /** Distilled single/multi-step schedules (e.g. SD Turbo). */
    DISTILLED_EULER("Distilled Euler"),
    /** Variance-preserving ODE used by DDPM training. */
    DDPM("DDPM");

    private final String displayName;

    SchedulerType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}