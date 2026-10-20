package atri.palaash.jforge.engine.scheduler;

/**
 * A simple {@code float[][][][]} latent tensor (batch × channel × height × width)
 * carrier so schedulers can be expressed without ONNX Runtime types.
 */
public record FloatLatents(
        float[][][][] data,
        int channels,
        int height,
        int width
) {

    public static FloatLatents of(float[][][][] data) {
        int ch = data[0].length;
        int h = data[0][0].length;
        int w = data[0][0][0].length;
        return new FloatLatents(data, ch, h, w);
    }

    public float[][][][] array() {
        return data;
    }
}