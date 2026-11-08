package local.tvbox.castlauncher;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/** Shared 1280 x 720 artwork. Views scale it to the actual television resolution. */
final class TvArtwork {
    static final int INK = Color.rgb(238, 242, 245);
    static final int MUTED = Color.rgb(175, 190, 202);
    static final int ACCENT = Color.rgb(154, 230, 199);
    private static final int NETWORK_ONLINE = Color.rgb(46, 211, 116);
    private static final int NETWORK_OFFLINE = Color.rgb(255, 174, 66);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap standbyBackground;

    TvArtwork() {
        this(null);
    }

    TvArtwork(Bitmap background) {
        standbyBackground = background;
        paint.setFilterBitmap(true);
    }

    void standby(Canvas canvas, String clock, String date, NetworkDisplay connection) {
        standby(canvas, clock, date, connection, connection.connected
                ? ReceiverStatus.ready("大屏投屏 · A1B2", "局域网地址 192.0.2.23")
                : new ReceiverStatus("大屏投屏 · A1B2", "投屏服务等待网络", "请先连接网络", false));
    }

    void standby(Canvas canvas, String clock, String date, NetworkDisplay connection, ReceiverStatus receiver) {
        if (standbyBackground != null) {
            paint.setColor(INK);
            canvas.drawBitmap(standbyBackground, null, new RectF(0, 0, 1280, 720), paint);
            paint.setShader(new LinearGradient(0, 0, 1280, 0,
                    Color.argb(100, 11, 24, 35), Color.argb(28, 15, 27, 38), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 1280, 720, paint);
            paint.setShader(new LinearGradient(0, 535, 0, 720,
                    Color.TRANSPARENT, Color.argb(105, 10, 22, 33), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 535, 1280, 720, paint);
            paint.setShader(null);
        } else {
            paint.setShader(new LinearGradient(0, 0, 1280, 720,
                    Color.rgb(13, 22, 34), Color.rgb(22, 38, 48), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 1280, 720, paint);
            paint.setShader(null);
        }

        castIcon(canvas, 64, 51, 0.65f, ACCENT);
        text(canvas, "大屏投屏", 112, 77, 23, INK, true);
        text(canvas, "在家，慢慢看", 112, 102, 13, MUTED, false);
        textRight(canvas, clock, 1216, 79, 32, INK);
        textRight(canvas, date, 1216, 104, 13, MUTED);
        line(canvas, 64, 133, 1216, 133, Color.argb(30, 255, 255, 255), 1);

        pill(canvas, connection.label, 64, 208, connection.connected);
        text(canvas, "好内容，", 64, 333, 62, INK, true);
        text(canvas, "大屏看。", 64, 414, 62, INK, true);
        text(canvas, connection.instruction, 68, 469, 21, MUTED, false);
        text(canvas, "剩下的时间，留给观看。", 68, 502, 21, MUTED, false);

        rounded(canvas, 753, 205, 1216, 520, 26, standbyBackground != null
                ? Color.argb(130, 15, 28, 39) : Color.argb(24, 255, 255, 255));
        castIcon(canvas, 798, 248, 0.8f, ACCENT);
        text(canvas, "在投屏列表中选择", 798, 347, 17, MUTED, false);
        paint.setTextSize(36);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text(canvas, ellipsize(receiver.name, 376), 794, 407, 36, INK, true);
        boolean available = receiver.ready && connection.connected;
        paint.setColor(available ? NETWORK_ONLINE : NETWORK_OFFLINE);
        canvas.drawCircle(803, 447, 4.5f, paint);
        text(canvas, connection.connected ? receiver.label : "投屏服务等待网络", 819, 453, 16, available ? ACCENT : NETWORK_OFFLINE, false);
        line(canvas, 798, 478, 1170, 478, Color.argb(32, 255, 255, 255), 1);
        text(canvas, "菜单键  名称 / 网络 / 默认桌面", 798, 503, 14, MUTED, false);

        line(canvas, 64, 596, 1216, 596, Color.argb(30, 255, 255, 255), 1);
        step(canvas, 64, "01", connection.wifi ? "连接同一 Wi-Fi" : "连接同一网络", "手机与电视盒子", 645);
        step(canvas, 464, "02", "打开视频投屏", "在播放器中点击投屏", 645);
        step(canvas, 864, "03", "选择上方设备名称", "播放后即可用遥控器控制", 645);
    }

    void player(Canvas canvas, String title, String state, int position, int duration,
            int bufferedPercent, boolean playing, boolean controls, String feedback,
            String message, boolean error) {
        if (message != null) {
            rounded(canvas, 370, 243, 910, 443, 22, Color.argb(232, 15, 25, 37));
            castIcon(canvas, 611, 271, 0.7f, error ? MUTED : ACCENT);
            textCentered(canvas, message, 640, 362, 25, INK, true);
            textCentered(canvas, error ? "按返回退出，再从手机重新投屏" : "视频就绪后将自动播放",
                    640, 402, 16, MUTED, false);
        }
        if (feedback != null && controls) {
            rounded(canvas, 488, 282, 792, 377, 18, Color.argb(218, 15, 25, 37));
            textCentered(canvas, feedback, 640, 321, 19, ACCENT, true);
            textCentered(canvas, time(position), 640, 359, 28, INK, true);
        }
        if (!controls) return;
        paint.setShader(new LinearGradient(0, 488, 0, 720,
                Color.TRANSPARENT, Color.argb(244, 7, 12, 19), Shader.TileMode.CLAMP));
        canvas.drawRect(0, 488, 1280, 720, paint);
        paint.setShader(null);
        text(canvas, state, 64, 546, 13, ACCENT, true);
        paint.setTextSize(26);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        text(canvas, ellipsize(title, 760), 64, 583, 26, INK, true);
        textRight(canvas, duration > 0 ? time(position) + "  /  " + time(duration)
                : time(position) + "  /  --:--", 1216, 583, 19, INK);
        rounded(canvas, 64, 613, 1216, 618, 2.5f, Color.argb(65, 255, 255, 255));
        if (duration > 0) {
            float progress = Math.max(0, Math.min(1, (float) position / duration));
            float buffered = Math.max(0, Math.min(1, bufferedPercent / 100f));
            if (buffered > 0) rounded(canvas, 64, 613, 64 + 1152 * buffered, 618,
                    2.5f, Color.argb(100, 255, 255, 255));
            if (progress > 0) rounded(canvas, 64, 613, 64 + 1152 * progress, 618,
                    2.5f, ACCENT);
            paint.setColor(ACCENT);
            canvas.drawCircle(64 + 1152 * progress, 615.5f, 6, paint);
        }
        rounded(canvas, 64, 642, 151, 684, 11, Color.argb(29, 255, 255, 255));
        textCentered(canvas, playing ? "暂停" : "播放", 107.5f, 670, 17, INK, true);
        text(canvas, "左右  快退 / 快进     ·     长按连续跳转", 179, 670, 15, MUTED, false);
        textRight(canvas, "确定  播放 / 暂停     返回  退出", 1216, 670, 15, MUTED);
    }

    private void step(Canvas canvas, float x, String number, String title, String subtitle, float y) {
        text(canvas, number, x, y, 22, ACCENT, false);
        text(canvas, title, x + 52, y - 2, 18, INK, true);
        text(canvas, subtitle, x + 52, y + 27, 14, MUTED, false);
    }

    private void pill(Canvas canvas, String title, float x, float y, boolean connected) {
        paint.setTextSize(15);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        String display = ellipsize(title, 572);
        float width = Math.max(167, paint.measureText(display) + 58);
        rounded(canvas, x, y, x + width, y + 35, 17.5f, standbyBackground != null
                ? Color.argb(195, 19, 37, 42) : Color.argb(110, 19, 37, 42));
        paint.setColor(connected ? NETWORK_ONLINE : NETWORK_OFFLINE);
        paint.setStyle(connected ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        canvas.drawCircle(x + 20, y + 17.5f, 6, paint);
        // Paint is shared by all artwork; the offline ring must not affect later drawing.
        paint.setStyle(Paint.Style.FILL);
        text(canvas, display, x + 39, y + 24, 15, connected ? INK : NETWORK_OFFLINE, false);
    }

    private void castIcon(Canvas canvas, float x, float y, float scale, int color) {
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(scale, scale);
        line(canvas, 0, 0, 58, 0, color, 3);
        line(canvas, 58, 0, 58, 42, color, 3);
        line(canvas, 58, 42, 32, 42, color, 3);
        line(canvas, 0, 0, 0, 12, color, 3);
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        canvas.drawArc(new RectF(-25, 17, 25, 67), -90, 90, false, paint);
        canvas.drawArc(new RectF(-14, 28, 14, 56), -90, 90, false, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(0, 42, 3, paint);
        canvas.restore();
    }

    private void text(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(text, x, y, paint);
    }

    private void textCentered(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
        text(canvas, "", x, y, size, color, bold);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x, y, paint);
    }

    private void textRight(Canvas canvas, String text, float x, float y, float size, int color) {
        text(canvas, "", x, y, size, color, false);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(text, x, y, paint);
    }

    private void line(Canvas canvas, float x1, float y1, float x2, float y2, int color, float width) {
        paint.setColor(color);
        paint.setStrokeWidth(width);
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    private void rounded(Canvas canvas, float left, float top, float right, float bottom, float radius, int color) {
        paint.setColor(color);
        canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, paint);
    }

    static String time(int milliseconds) {
        int total = Math.max(0, milliseconds) / 1000;
        int hours = total / 3600;
        int minutes = (total / 60) % 60;
        int seconds = total % 60;
        return hours > 0 ? two(hours) + ":" + two(minutes) + ":" + two(seconds)
                : two(minutes) + ":" + two(seconds);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private String ellipsize(String value, float maxWidth) {
        if (value == null || value.trim().isEmpty()) return "正在播放";
        if (paint.measureText(value) <= maxWidth) return value;
        int end = value.length();
        while (end > 0 && paint.measureText(value.substring(0, end) + "…") > maxWidth) end--;
        return value.substring(0, end) + "…";
    }
}
