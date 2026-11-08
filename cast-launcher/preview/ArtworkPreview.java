package local.tvbox.castlauncher;

import android.graphics.Canvas;
import android.graphics.BitmapFactory;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class ArtworkPreview {
    private static final TvArtwork artwork = new TvArtwork();

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        directory.mkdirs();
        BufferedImage standby = image();
        Graphics2D graphics = standby.createGraphics();
        TvArtwork standbyArtwork = new TvArtwork(BitmapFactory.decodeFile(args[1]));
        standbyArtwork.standby(new Canvas(graphics), "20:36", "11月7日  星期六",
                NetworkDisplay.wifi("\"客厅 Wi-Fi\"", null));
        graphics.dispose();
        verifyNetworkIndicator(standby, true);
        ImageIO.write(standby, "png", new File(directory, "standby.png"));
        ImageIO.write(standby, "png", new File(directory, "standby-dusk-v1.png"));
        ImageIO.write(standby, "png", new File(directory, "standby-wifi.png"));

        assertEqual("客厅 Wi-Fi", NetworkDisplay.wifi("\"客厅 Wi-Fi\"", null).ssid);
        assertEqual("Wi-Fi 已连接 · 客厅 Wi-Fi", NetworkDisplay.wifi("\"客厅 Wi-Fi\"", null).label);
        assertEqual("Guest Network", NetworkDisplay.wifi("<unknown ssid>", "\"Guest Network\"").ssid);
        assertEqual(null, NetworkDisplay.wifi("<unknown ssid>", "0x").ssid);
        assertEqual(null, NetworkDisplay.disconnected().ssid);
        assertEqual("有线网络已连接", NetworkDisplay.wired().label);
        if (NetworkDisplay.disconnected().connected) throw new AssertionError("Disconnected state");
        renderNetwork(directory, standbyArtwork, "standby-wifi-long.png",
                NetworkDisplay.wifi("Home WiFi - Long Network Name 123", null));
        renderNetwork(directory, standbyArtwork, "standby-wired.png", NetworkDisplay.wired());
        renderNetwork(directory, standbyArtwork, "standby-disconnected.png", NetworkDisplay.disconnected());
        renderNetwork(directory, standbyArtwork, "standby-wifi-unknown.png", NetworkDisplay.wifi(null, null));
        renderReceiver(directory, standbyArtwork, "standby-service-starting.png", ReceiverStatus.starting("大屏投屏 · A1B2"));
        renderReceiver(directory, standbyArtwork, "standby-service-error.png",
                new ReceiverStatus("大屏投屏 · A1B2", "投屏服务启动失败", "请重试", false));

        BufferedImage player = landscape();
        graphics = player.createGraphics();
        artwork.player(new Canvas(graphics), "周末的风景", "正在播放", 1104000, 3130000,
                61, true, true, "快进  10 秒", null, false);
        graphics.dispose();
        ImageIO.write(player, "png", new File(directory, "player.png"));

        BufferedImage hidden = image();
        graphics = hidden.createGraphics();
        artwork.player(new Canvas(graphics), "测试", "正在播放", 10000, 100000,
                50, true, false, null, null, false);
        graphics.dispose();
        for (int y = 0; y < hidden.getHeight(); y++) {
            for (int x = 0; x < hidden.getWidth(); x++) {
                if (hidden.getRGB(x, y) != 0) throw new AssertionError("Hidden controls obscure video");
            }
        }
        if (!"01:02:03".equals(TvArtwork.time(3723000))) throw new AssertionError("Long duration format");
        if (!"00:00".equals(TvArtwork.time(-100))) throw new AssertionError("Negative position format");
        System.out.println("Rendered Wi-Fi / wired / disconnected / unavailable SSID previews; green filled / orange outlined indicators, SSID labels and hidden-control transparency verified.");
    }

    private static void assertEqual(String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void renderNetwork(File directory, TvArtwork renderer, String filename,
            NetworkDisplay connection) throws Exception {
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        renderer.standby(new Canvas(graphics), "20:36", "11月7日  星期六", connection);
        graphics.dispose();
        verifyNetworkIndicator(image, connection.connected);
        ImageIO.write(image, "png", new File(directory, filename));
    }

    private static void verifyNetworkIndicator(BufferedImage image, boolean connected) {
        int center = image.getRGB(84, 225);
        if (connected) {
            if (center != 0xff2ed374) throw new AssertionError("Connected indicator must be saturated green");
        } else {
            if (image.getRGB(90, 225) != 0xffffae42) throw new AssertionError("Offline indicator must have an orange outline");
            if (center == 0xffffae42) throw new AssertionError("Offline indicator must be hollow");
        }
    }

    private static void renderReceiver(File directory, TvArtwork renderer, String filename, ReceiverStatus status) throws Exception {
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        renderer.standby(new Canvas(graphics), "20:36", "11月7日  星期六", NetworkDisplay.wifi("客厅 Wi-Fi", null), status);
        graphics.dispose();
        verifyNetworkIndicator(image, true);
        if (image.getRGB(803, 447) != 0xffffae42) throw new AssertionError("Connected Wi-Fi must not imply receiver readiness");
        ImageIO.write(image, "png", new File(directory, filename));
    }

    private static BufferedImage image() {
        return new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
    }

    // Stylized video placeholder only; the Android view displays the actual cast video here.
    private static BufferedImage landscape() {
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        graphics.setPaint(new GradientPaint(0, 0, new Color(36, 69, 84),
                0, 500, new Color(210, 178, 135)));
        graphics.fillRect(0, 0, 1280, 720);
        graphics.setColor(new Color(237, 205, 150));
        graphics.fillOval(912, 130, 92, 92);
        graphics.setColor(new Color(60, 83, 91));
        graphics.fill(new Polygon(new int[]{0, 0, 190, 375, 532, 768, 977, 1280, 1280},
                new int[]{720, 463, 289, 436, 356, 473, 342, 477, 720}, 9));
        graphics.setColor(new Color(25, 51, 63));
        graphics.fill(new Polygon(new int[]{0, 0, 214, 385, 670, 929, 1280, 1280},
                new int[]{720, 583, 442, 568, 427, 573, 482, 720}, 8));
        graphics.dispose();
        return image;
    }
}
