package atri.palaash.jforge.engine;

/**
 * Granular pipeline capability flags. The UI is generated from these —
 * never from hard-coded model-name conditionals.
 */
public enum Capability {
    /** Text → image generation. */
    TEXT_TO_IMAGE,
    /** Image → image generation. */
    IMAGE_TO_IMAGE,
    /** True inpainting with a real mask (+ source). */
    INPAINT,
    /** Outpainting into an extended canvas region. */
    OUTPAINT,
    /** Negative prompt support. */
    NEGATIVE_PROMPT,
    /** Classifier-free guidance (meaningful CFG scaling). */
    CFG,
    /** LoRA application. */
    LORA,
    /** ControlNet conditioning. */
    CONTROLNET,
    /** Simple reference-image conditioning. */
    REFERENCE_IMAGE,
    /** IP-Adapter style reference conditioning. */
    IP_ADAPTER,
    /** CLIP layer skip. */
    CLIP_SKIP,
    /** Custom external VAE. */
    CUSTOM_VAE,
    /** Trainable (LoRA training target). */
    TRAINING,
    /** Quantization selection. */
    QUANTIZATION,
    /** Video generation. */
    VIDEO,
    /** Audio generation. */
    AUDIO
}