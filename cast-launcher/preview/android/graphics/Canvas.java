package android.graphics;

import java.awt.BasicStroke;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;

/** Desktop adapter for previewing the production artwork, never bundled in the APK. */
public final class Canvas {
    private final Graphics2D graphics;
    private final ArrayDeque<AffineTransform> transforms = new ArrayDeque<AffineTransform>();
    public Canvas(Graphics2D graphics) {
        this.graphics = graphics;
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    public void save() { transforms.push(graphics.getTransform()); }
    public void restore() { graphics.setTransform(transforms.pop()); }
    public void scale(float x, float y) { graphics.scale(x, y); }
    public void translate(float x, float y) { graphics.translate(x, y); }
    public void drawBitmap(Bitmap bitmap, Object source, RectF destination, Paint paint) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(bitmap.image, (int) destination.left, (int) destination.top,
                (int) destination.right, (int) destination.bottom,
                0, 0, bitmap.image.getWidth(), bitmap.image.getHeight(), null);
    }
    private void apply(Paint paint) {
        if (paint.shader instanceof LinearGradient) {
            LinearGradient gradient = (LinearGradient) paint.shader;
            graphics.setPaint(new GradientPaint(gradient.x0, gradient.y0, color(gradient.color0),
                    gradient.x1, gradient.y1, color(gradient.color1)));
        } else graphics.setColor(color(paint.color));
        graphics.setStroke(new BasicStroke(paint.strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }
    private java.awt.Color color(int value) { return new java.awt.Color(value, true); }
    private void shape(Shape shape, Paint paint) {
        apply(paint);
        if (paint.style == Paint.Style.STROKE) graphics.draw(shape); else graphics.fill(shape);
    }
    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        shape(new Rectangle2D.Float(left, top, right - left, bottom - top), paint);
    }
    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        shape(new RoundRectangle2D.Float(rect.left, rect.top, rect.right - rect.left,
                rect.bottom - rect.top, rx * 2, ry * 2), paint);
    }
    public void drawCircle(float x, float y, float radius, Paint paint) {
        shape(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2), paint);
    }
    public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
        apply(paint);
        graphics.draw(new Line2D.Float(x1, y1, x2, y2));
    }
    public void drawArc(RectF rect, float start, float sweep, boolean center, Paint paint) {
        shape(new Arc2D.Float(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top,
                -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN), paint);
    }
    public void drawText(String text, float x, float y, Paint paint) {
        apply(paint);
        graphics.setFont(paint.font());
        float width = paint.measureText(text);
        if (paint.align == Paint.Align.CENTER) x -= width / 2;
        if (paint.align == Paint.Align.RIGHT) x -= width;
        graphics.drawString(text, x, y);
    }
}
