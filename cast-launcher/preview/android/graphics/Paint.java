package android.graphics;

public final class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL, STROKE }
    public enum Align { LEFT, CENTER, RIGHT }
    int color;
    float textSize = 16, strokeWidth = 1;
    Shader shader;
    Typeface typeface = Typeface.create("sans-serif", Typeface.NORMAL);
    Style style = Style.FILL;
    Align align = Align.LEFT;
    public Paint(int flags) {}
    public void setColor(int color) { this.color = color; }
    public void setTextSize(float size) { this.textSize = size; }
    public void setTypeface(Typeface face) { this.typeface = face; }
    public void setTextAlign(Align align) { this.align = align; }
    public void setStrokeWidth(float width) { this.strokeWidth = width; }
    public void setStyle(Style style) { this.style = style; }
    public void setShader(Shader shader) { this.shader = shader; }
    public void setFilterBitmap(boolean filter) {}
    public float measureText(String text) {
        return (float) font().getStringBounds(text,
                new java.awt.font.FontRenderContext(null, true, true)).getWidth();
    }
    java.awt.Font font() {
        return new java.awt.Font("PingFang SC", typeface.style == Typeface.BOLD
                ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 16).deriveFont(textSize);
    }
}
