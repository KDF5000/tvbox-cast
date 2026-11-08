package android.graphics;

public final class Typeface {
    public static final int NORMAL = 0, BOLD = 1;
    public int style;
    public static Typeface create(String family, int style) {
        Typeface face = new Typeface();
        face.style = style;
        return face;
    }
}
