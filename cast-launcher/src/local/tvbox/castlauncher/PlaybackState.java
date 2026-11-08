package local.tvbox.castlauncher;

/** Thread-safe, actual player state shared by HTTP queries and the Android UI. */
final class PlaybackState {
    static final PlaybackState INSTANCE = new PlaybackState();
    private String uri = "", metadata = "", title = "", transport = "NO_MEDIA_PRESENT";
    private String status = "OK";
    private int position, duration, volume = 50;
    private boolean muted;
    private boolean playRequested;
    private long generation;

    synchronized long begin(String url, String name, String didl) {
        return begin(url, name, didl, true);
    }

    synchronized long begin(String url, String name, String didl, boolean autoplay) {
        generation++;
        uri = url;
        title = name == null ? "手机投屏" : name;
        metadata = didl == null ? "" : didl;
        transport = "TRANSITIONING";
        status = "OK";
        playRequested = autoplay;
        position = duration = 0;
        return generation;
    }

    synchronized void update(long token, String state, int pos, int length, boolean failed) {
        if (token != generation || uri.isEmpty()) return;
        transport = state;
        status = failed ? "ERROR_OCCURRED" : "OK";
        position = Math.max(0, pos);
        duration = Math.max(0, length);
    }

    synchronized void clear(long token) {
        if (token != generation) return;
        generation++;
        uri = metadata = title = "";
        transport = "NO_MEDIA_PRESENT";
        status = "OK";
        position = duration = 0;
        playRequested = false;
    }

    synchronized void requestPlay(long token, boolean value) { if (token == generation) playRequested = value; }

    synchronized void volume(int value, boolean mute) {
        volume = Math.max(0, Math.min(100, value));
        muted = mute;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(uri, metadata, title, transport, status, position, duration,
                volume, muted, generation, playRequested);
    }

    static final class Snapshot {
        final String uri, metadata, title, transport, status;
        final int position, duration, volume;
        final boolean muted;
        final boolean playRequested;
        final long generation;
        Snapshot(String u, String m, String t, String s, String e, int p, int d,
                int v, boolean mute, long g, boolean requested) {
            uri = u; metadata = m; title = t; transport = s; status = e;
            position = p; duration = d; volume = v; muted = mute; generation = g;
            playRequested = requested;
        }
    }
}
