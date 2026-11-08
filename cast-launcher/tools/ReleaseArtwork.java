import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Original code-drawn cast icon: no stock image, generated photo or vendor logo. */
public final class ReleaseArtwork {
    private static void icon(Graphics2D g, int x, int y, float scale) {
        g.translate(x, y); g.scale(scale, scale);
        g.setColor(new Color(154, 230, 199));
        g.setStroke(new BasicStroke(7, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(27, 28, 139, 28); g.drawLine(139, 28, 139, 112);
        g.drawLine(139, 112, 89, 112); g.drawLine(27, 28, 27, 52);
        g.drawArc(-11, 74, 76, 76, 0, 90); g.drawArc(7, 92, 40, 40, 0, 90);
        g.fillOval(22, 108, 11, 11);
    }
    public static void main(String[] args) throws Exception {
        File folder = new File(args[0]); folder.mkdirs();
        BufferedImage icon = new BufferedImage(192, 192, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(16, 34, 45)); g.fillRoundRect(0, 0, 192, 192, 42, 42);
        icon(g, 9, 16, 1.12f); g.dispose();
        ImageIO.write(icon, "png", new File(folder, "app_icon.png"));
        BufferedImage banner = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        g = banner.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(16, 34, 45)); g.fillRect(0, 0, 320, 180);
        icon(g, 88, 20, 0.92f); g.dispose();
        ImageIO.write(banner, "png", new File(folder, "app_banner.png"));
    }
}
