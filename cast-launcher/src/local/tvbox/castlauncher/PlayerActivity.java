package local.tvbox.castlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

public final class PlayerActivity extends Activity {
    private static final String TAG = "TvBoxPlayer";
    private static final int SEEK_STEP_MS = 10000;
    private static final int HOLD_SEEK_STEP_MS = 5000;
    private static final int HOLD_START_DELAY_MS = 450;
    private static final int HOLD_REPEAT_INTERVAL_MS = 250;
    public static final String ACTION_SET_MEDIA = "local.tvbox.action.SET_MEDIA";
    public static final String ACTION_PLAY = "local.tvbox.action.PLAY";
    public static final String ACTION_PAUSE = "local.tvbox.action.PAUSE";
    public static final String ACTION_SEEK = "local.tvbox.action.SEEK";
    public static final String ACTION_STOP = "local.tvbox.action.STOP";
    private static String activeUrl;
    private static PlayerActivity current;
    private long playbackToken;
    private VideoView video;
    private TextView subtitleView;
    private PlayerOverlay overlay;
    private FrameLayout.LayoutParams subtitleLayout;
    private boolean prepared;
    private boolean hasPlayed, completed;
    private boolean hasSession;
    private boolean playWhenReady = true;
    private int mediaGeneration;
    private final Handler uiHandler = new Handler();
    private final Runnable openTimeout = new Runnable() {
        @Override public void run() {
            if (prepared || !hasSession || PlaybackState.INSTANCE.snapshot().generation != playbackToken) return;
            video.stopPlayback();
            overlay.message = "视频打开超时，请重新投屏";
            overlay.error = true;
            overlay.state = "播放遇到问题";
            overlay.playing = false;
            PlaybackState.INSTANCE.update(playbackToken, "STOPPED", 0, 0, true);
            showControls();
        }
    };
    private final Runnable hideControls = new Runnable() {
        @Override public void run() {
            if (heldSeekDirection != 0) {
                uiHandler.postDelayed(this, 1000);
                return;
            }
            overlay.controlsVisible = false;
            overlay.feedback = null;
            positionSubtitles();
            overlay.invalidate();
        }
    };
    private final Runnable hideFeedback = new Runnable() {
        @Override public void run() {
            overlay.feedback = null;
            overlay.invalidate();
        }
    };
    private final ArrayList<Cue> subtitleCues = new ArrayList<Cue>();
    private final Handler subtitleHandler = new Handler();
    private final Handler seekHandler = new Handler();
    private int heldSeekDirection;
    private int desiredSeekPositionMs = -1;
    private long lastSeekInputAtMs;
    private final Runnable subtitleTicker = new Runnable() {
        @Override public void run() {
            publishPlayback();
            updateSubtitle(video == null ? 0 : video.getCurrentPosition());
            updateControls();
            subtitleHandler.postDelayed(this, 200);
        }
    };
    private final Runnable holdSeekTicker = new Runnable() {
        @Override public void run() {
            if (heldSeekDirection == 0) return;
            seekBy(heldSeekDirection * HOLD_SEEK_STEP_MS);
            seekHandler.postDelayed(this, HOLD_REPEAT_INTERVAL_MS);
        }
    };
    private final Runnable commitSeek = new Runnable() {
        @Override public void run() {
            if (desiredSeekPositionMs < 0 || video == null) return;
            video.seekTo(desiredSeekPositionMs);
            long idleMs = SystemClock.uptimeMillis() - lastSeekInputAtMs;
            if (idleMs < 1200) {
                seekHandler.postDelayed(this, 180);
            } else {
                desiredSeekPositionMs = -1;
            }
        }
    };

    public static String currentUrl() {
        return activeUrl;
    }

    static PlayerActivity currentPlayer() { return current; }
    long playbackToken() { return playbackToken; }

    void remoteCommand(String action, int value) throws DlnaProtocol.Fault {
        if ("Play".equals(action) || "Pause".equals(action)) {
            playWhenReady = "Play".equals(action);
            PlaybackState.INSTANCE.requestPlay(playbackToken, playWhenReady);
            if (prepared) { if (playWhenReady) { completed = false; hasPlayed = true; video.start(); } else video.pause(); }
        } else if ("Seek".equals(action)) {
            if (!prepared || video.getDuration() <= 0) throw new DlnaProtocol.Fault(710, "Stream Not Seekable");
            seekToPosition(value);
        } else if ("Stop".equals(action)) {
            stopSession(); finish(); return;
        } else throw new DlnaProtocol.Fault(401, "Invalid Action");
        publishPlayback(); showControls();
    }

    private void publishPlayback() {
        ReceiverService.refreshVolume(this);
        if (video == null || PlaybackState.INSTANCE.snapshot().generation != playbackToken) return;
        if (prepared) PlaybackState.INSTANCE.update(playbackToken,
                video.isPlaying() ? "PLAYING" : completed || !hasPlayed ? "STOPPED" : "PAUSED_PLAYBACK",
                video.getCurrentPosition(), video.getDuration(), false);
    }

    private void stopSession() {
        uiHandler.removeCallbacks(openTimeout);
        video.stopPlayback(); prepared = false;
        PlaybackState.INSTANCE.clear(playbackToken);
        activeUrl = null; resetSeeking();
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = this;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        video = new VideoView(this);
        video.setBackgroundColor(Color.BLACK);
        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override public void onPrepared(MediaPlayer player) {
                if (PlaybackState.INSTANCE.snapshot().generation != playbackToken) return;
                uiHandler.removeCallbacks(openTimeout);
                prepared = true;
                video.setBackgroundColor(Color.TRANSPARENT);
                player.setScreenOnWhilePlaying(true);
                selectEmbeddedSubtitle(player);
                overlay.message = null;
                overlay.error = false;
                playWhenReady = PlaybackState.INSTANCE.snapshot().playRequested;
                if (playWhenReady) { hasPlayed = true; video.start(); }
                publishPlayback();
                updateControls();
                showControls();
            }
        });
        video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override public boolean onInfo(MediaPlayer player, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    overlay.message = "正在缓冲";
                    overlay.invalidate();
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    overlay.message = null;
                    overlay.invalidate();
                }
                return false;
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override public boolean onError(MediaPlayer player, int what, int extra) {
                uiHandler.removeCallbacks(openTimeout);
                Log.w(TAG, "playback failed: " + what + "/" + extra);
                prepared = false;
                overlay.message = "暂时无法播放这个视频";
                overlay.error = true;
                overlay.state = "播放遇到问题";
                overlay.playing = false;
                PlaybackState.INSTANCE.update(playbackToken, "STOPPED", video.getCurrentPosition(), 0, true);
                showControls();
                return true;
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override public void onCompletion(MediaPlayer player) {
                overlay.state = "播放结束";
                completed = true;
                overlay.playing = false;
                playWhenReady = false;
                PlaybackState.INSTANCE.requestPlay(playbackToken, false);
                PlaybackState.INSTANCE.update(playbackToken, "STOPPED", video.getDuration(), video.getDuration(), false);
                showControls();
            }
        });
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(video, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));

        subtitleView = new TextView(this);
        subtitleView.setTextColor(Color.WHITE);
        subtitleView.setTextSize(28);
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setShadowLayer(6, 2, 2, Color.BLACK);
        subtitleView.setPadding(24, 8, 24, 8);
        subtitleView.setBackgroundColor(Color.TRANSPARENT);
        subtitleView.setVisibility(View.GONE);
        subtitleLayout = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        subtitleLayout.setMargins(40, 0, 40, 54);
        root.addView(subtitleView, subtitleLayout);
        overlay = new PlayerOverlay(this, new PlayerOverlay.Listener() {
            @Override public void revealControls() { showControls(); }
            @Override public void togglePlayback() { PlayerActivity.this.togglePlayback(); }
            @Override public void seekToFraction(float fraction) {
                seekToPosition((int) (video.getDuration() * fraction));
            }
        });
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(android.content.Intent intent) {
        String action = intent.getAction();
        Uri uri = intent.getData();
        if (ACTION_SET_MEDIA.equals(action) && uri != null) {
            long suppliedToken = intent.getLongExtra("media_generation", -1);
            if (suppliedToken != -1 && suppliedToken != PlaybackState.INSTANCE.snapshot().generation) {
                if (!hasSession) finish();
                return;
            }
            hasSession = true;
            activeUrl = uri.toString();
            mediaGeneration++;
            prepared = false;
            hasPlayed = completed = false;
            playbackToken = suppliedToken == -1 ? PlaybackState.INSTANCE.begin(activeUrl,
                    intent.getStringExtra("media_title"), intent.getStringExtra("media_metadata")) : suppliedToken;
            playWhenReady = PlaybackState.INSTANCE.snapshot().playRequested;
            overlay.title = intent.getStringExtra("media_title");
            if (overlay.title == null || overlay.title.trim().isEmpty()) overlay.title = "手机投屏";
            overlay.title = overlay.title.replaceAll("\\s+", " ").trim();
            overlay.message = "正在打开视频";
            overlay.error = false;
            overlay.feedback = null;
            overlay.position = 0;
            overlay.duration = 0;
            overlay.state = "手机投屏";
            overlay.playing = false;
            resetSeeking();
            subtitleCues.clear();
            subtitleView.setText("");
            subtitleView.setVisibility(View.GONE);
            video.stopPlayback();
            video.setVideoURI(uri);
            uiHandler.removeCallbacks(openTimeout);
            uiHandler.postDelayed(openTimeout, 20000);
            video.requestFocus();
            String subtitleUrl = intent.getStringExtra("subtitle_url");
            if (subtitleUrl == null) subtitleUrl = DlnaProtocol.subtitleFromVideoUrl(activeUrl);
            if (subtitleUrl != null) loadExternalSubtitle(subtitleUrl);
            showControls();
        } else if (ACTION_PLAY.equals(action)) {
            playWhenReady = true;
            PlaybackState.INSTANCE.requestPlay(playbackToken, true);
            if (prepared) { completed = false; hasPlayed = true; video.start(); }
            publishPlayback();
            showControls();
        } else if (ACTION_PAUSE.equals(action)) {
            playWhenReady = false;
            PlaybackState.INSTANCE.requestPlay(playbackToken, false);
            if (prepared) video.pause();
            publishPlayback();
            showControls();
        } else if (ACTION_SEEK.equals(action)) {
            seekToPosition(intent.getIntExtra("position_ms", 0));
        } else if (ACTION_STOP.equals(action)) {
            stopSession();
            finish();
        }
    }

    // VideoView consumes arrow keys itself; route playback keys before child dispatch.
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MEDIA_STOP
                || code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
                || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || code == KeyEvent.KEYCODE_MEDIA_REWIND
                || code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || code == KeyEvent.KEYCODE_DPAD_CENTER
                || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_DPAD_UP
                || code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_MENU) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(code, event);
            if (event.getAction() == KeyEvent.ACTION_UP) {
                onKeyUp(code, event);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MEDIA_STOP) {
            stopSession();
            finish();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            if (event.getRepeatCount() == 0) {
                seekBy(SEEK_STEP_MS);
                startHoldSeeking(1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            if (event.getRepeatCount() == 0) {
                seekBy(-SEEK_STEP_MS);
                startHoldSeeking(-1);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0) togglePlayback();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_MENU) {
            showControls();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            stopHoldSeeking();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void seekBy(int deltaMs) {
        int duration = video.getDuration();
        int base = desiredSeekPositionMs >= 0
                ? desiredSeekPositionMs : video.getCurrentPosition();
        int target = base + deltaMs;
        if (target < 0) target = 0;
        if (duration > 0 && target > duration) target = duration;
        seekToPosition(target);
    }

    private void seekToPosition(int positionMs) {
        if (!prepared) return;
        int duration = video.getDuration();
        int previous = desiredSeekPositionMs >= 0 ? desiredSeekPositionMs : video.getCurrentPosition();
        desiredSeekPositionMs = Math.max(0, positionMs);
        if (duration > 0 && desiredSeekPositionMs > duration) {
            desiredSeekPositionMs = duration;
        }
        lastSeekInputAtMs = SystemClock.uptimeMillis();
        video.seekTo(desiredSeekPositionMs);
        seekHandler.removeCallbacks(commitSeek);
        seekHandler.postDelayed(commitSeek, 180);
        int delta = desiredSeekPositionMs - previous;
        overlay.feedback = delta < 0 ? "快退  " + Math.abs(delta / 1000) + " 秒"
                : "快进  " + delta / 1000 + " 秒";
        uiHandler.removeCallbacks(hideFeedback);
        uiHandler.postDelayed(hideFeedback, 1100);
        showControls();
    }

    private void startHoldSeeking(int direction) {
        heldSeekDirection = direction;
        seekHandler.removeCallbacks(holdSeekTicker);
        seekHandler.postDelayed(holdSeekTicker, HOLD_START_DELAY_MS);
    }

    private void stopHoldSeeking() {
        heldSeekDirection = 0;
        seekHandler.removeCallbacks(holdSeekTicker);
    }

    private void resetSeeking() {
        stopHoldSeeking();
        desiredSeekPositionMs = -1;
        seekHandler.removeCallbacks(commitSeek);
    }

    private void togglePlayback() {
        if (prepared) {
            if (video.isPlaying()) video.pause(); else video.start();
            if (video.isPlaying()) { hasPlayed = true; completed = false; }
            playWhenReady = video.isPlaying();
            PlaybackState.INSTANCE.requestPlay(playbackToken, playWhenReady);
            publishPlayback();
        }
        showControls();
    }

    private void showControls() {
        overlay.controlsVisible = true;
        updateControls();
        positionSubtitles();
        overlay.invalidate();
        uiHandler.removeCallbacks(hideControls);
        uiHandler.postDelayed(hideControls, 4000);
    }

    private void updateControls() {
        if (overlay == null || !overlay.controlsVisible) return;
        overlay.position = desiredSeekPositionMs >= 0 ? desiredSeekPositionMs : video.getCurrentPosition();
        overlay.duration = video.getDuration();
        overlay.bufferedPercent = video.getBufferPercentage();
        overlay.playing = video.isPlaying();
        if (prepared) {
            overlay.state = overlay.playing ? "正在播放"
                    : overlay.duration > 0 && overlay.position >= overlay.duration ? "播放结束" : "已暂停";
        }
        overlay.invalidate();
    }

    private void positionSubtitles() {
        int height = getResources().getDisplayMetrics().heightPixels;
        subtitleLayout.bottomMargin = (int) ((overlay.controlsVisible ? 212 : 54) * height / 720f);
        subtitleView.setLayoutParams(subtitleLayout);
    }

    private void selectEmbeddedSubtitle(MediaPlayer player) {
        try {
            MediaPlayer.TrackInfo[] tracks = player.getTrackInfo();
            if (tracks == null) return;
            for (int i = 0; i < tracks.length; i++) {
                int type = tracks[i].getTrackType();
                Log.i(TAG, "track " + i + " type=" + type);
                if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT || type == 4) {
                    player.selectTrack(i);
                    Log.i(TAG, "selected subtitle track " + i);
                    return;
                }
            }
            Log.i(TAG, "no embedded subtitle track");
        } catch (Exception error) {
            Log.w(TAG, "cannot select embedded subtitle", error);
        }
    }

    private void loadExternalSubtitle(final String subtitleUrl) {
        final int generation = mediaGeneration;
        new AsyncTask<Void, Void, byte[]>() {
            @Override protected byte[] doInBackground(Void... unused) {
                InputStream input = null;
                try {
                    DlnaProtocol.mediaUrl(subtitleUrl);
                    URLConnection connection = new URL(subtitleUrl).openConnection();
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    if (connection.getContentLength() > 1048576) throw new java.io.IOException("Subtitle too large");
                    input = new BufferedInputStream(connection.getInputStream());
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > 1048576) throw new java.io.IOException("Subtitle too large");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                } catch (Exception error) {
                    Log.w(TAG, "Subtitle download failed: " + error.getClass().getSimpleName());
                    return null;
                } finally {
                    if (input != null) try { input.close(); } catch (Exception ignored) { }
                }
            }

            @Override protected void onPostExecute(byte[] data) {
                if (data == null || isFinishing() || generation != mediaGeneration) return;
                try {
                    String text = new String(data, "UTF-8");
                    if (text.indexOf('\ufffd') >= 0) text = new String(data, "GB18030");
                    parseSubtitles(text);
                    Log.i(TAG, "External subtitles loaded, cues=" + subtitleCues.size());
                } catch (Exception error) {
                    Log.w(TAG, "Cannot attach external subtitle: " + error.getClass().getSimpleName());
                }
            }
        }.execute();
    }

    private void parseSubtitles(String source) {
        subtitleCues.clear();
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        String[] blocks = normalized.split("\n\\s*\n");
        for (String block : blocks) {
            if (subtitleCues.size() >= 10000) break;
            String[] lines = block.split("\n");
            int timingLine = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("-->")) {
                    timingLine = i;
                    break;
                }
            }
            if (timingLine < 0) continue;
            String[] times = lines[timingLine].split("-->");
            if (times.length != 2) continue;
            long start = parseTimestamp(times[0].trim());
            String endToken = times[1].trim().split("\\s+")[0];
            long end = parseTimestamp(endToken);
            if (start < 0 || end <= start) continue;
            StringBuilder caption = new StringBuilder();
            for (int i = timingLine + 1; i < lines.length; i++) {
                if (caption.length() > 0) caption.append('\n');
                caption.append(lines[i]);
            }
            String clean = caption.toString().replaceAll("<[^>]+>", "")
                    .replaceAll("\\{\\\\[^}]+\\}", "").trim();
            if (!clean.isEmpty()) subtitleCues.add(new Cue(start, end, clean));
        }
    }

    private long parseTimestamp(String value) {
        try {
            String[] parts = value.replace(',', '.').split(":");
            double seconds = 0;
            for (String part : parts) seconds = seconds * 60 + Double.parseDouble(part);
            return (long) (seconds * 1000);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private void updateSubtitle(int positionMs) {
        String caption = null;
        for (Cue cue : subtitleCues) {
            if (positionMs >= cue.startMs && positionMs < cue.endMs) {
                caption = cue.text;
                break;
            }
        }
        if (caption == null) {
            subtitleView.setVisibility(View.GONE);
        } else {
            if (!caption.equals(subtitleView.getText().toString())) subtitleView.setText(caption);
            subtitleView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ReceiverService.playerVisible = true;
        subtitleHandler.removeCallbacks(subtitleTicker);
        subtitleHandler.post(subtitleTicker);
        if (overlay.controlsVisible) showControls();
    }

    @Override
    protected void onPause() {
        ReceiverService.playerVisible = false;
        subtitleHandler.removeCallbacks(subtitleTicker);
        uiHandler.removeCallbacks(hideControls);
        uiHandler.removeCallbacks(hideFeedback);
        stopHoldSeeking();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current == this) { current = null; stopSession(); }
        subtitleHandler.removeCallbacks(subtitleTicker);
        uiHandler.removeCallbacksAndMessages(null);
        mediaGeneration++;
        resetSeeking();
        super.onDestroy();
    }

    private static final class Cue {
        final long startMs;
        final long endMs;
        final String text;

        Cue(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }
}
