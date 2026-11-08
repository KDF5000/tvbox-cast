package android.graphics;

public final class LinearGradient extends Shader {
    public final float x0, y0, x1, y1;
    public final int color0, color1;
    public LinearGradient(float x0, float y0, float x1, float y1,
            int color0, int color1, TileMode mode) {
        this.x0 = x0; this.y0 = y0; this.x1 = x1; this.y1 = y1;
        this.color0 = color0; this.color1 = color1;
    }
}
