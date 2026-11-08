package local.tvbox.castlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/** Lightweight, transparent television controls; no separate system controller window. */
final class PlayerOverlay extends View {
    interface Listener {
        void revealControls();
        void togglePlayback();
        void seekToFraction(float fraction);
    }

    private final TvArtwork artwork = new TvArtwork();
    private final Listener listener;
    String title = "正在播放";
    String state = "正在播放";
    String feedback;
    String message;
    boolean error;
    boolean controlsVisible;
    boolean playing;
    int position;
    int duration;
    int bufferedPercent;

    PlayerOverlay(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setFocusable(false);
        setContentDescription("播放控制。确定键播放或暂停，左右键跳转，长按连续跳转，返回退出。");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 1280f, getHeight() / 720f);
        artwork.player(canvas, title, state, position, duration, bufferedPercent,
                playing, controlsVisible, feedback, message, error);
        canvas.restore();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            boolean wasVisible = controlsVisible;
            listener.revealControls();
            float x = event.getX() * 1280 / getWidth();
            float y = event.getY() * 720 / getHeight();
            if (wasVisible && x >= 64 && x <= 151 && y >= 642 && y <= 684) {
                listener.togglePlayback();
            } else if (wasVisible && duration > 0 && x >= 64 && x <= 1216
                    && y >= 595 && y <= 636) {
                listener.seekToFraction((x - 64) / 1152f);
            }
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
