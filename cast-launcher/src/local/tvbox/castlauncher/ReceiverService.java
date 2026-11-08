package local.tvbox.castlauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Owns discovery and HTTP lifetime. Normal app permissions only; never kills vendor services. */
public final class ReceiverService extends Service {
    static final String STATUS_CHANGED = "local.tvbox.castlauncher.RECEIVER_STATUS";
    private static final String TAG = "TvBoxReceiver";
    private static volatile ReceiverStatus status = ReceiverStatus.starting("大屏投屏");
    static volatile boolean homeVisible, playerVisible;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ScheduledExecutorService worker;
    private DlnaHttpServer http;
    private SsdpDiscovery ssdp;
    private String boundKey;
    private WifiManager.MulticastLock multicastLock;
    private PowerManager.WakeLock wakeLock;
    private volatile boolean destroyed;
    private final BroadcastReceiver network = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { checkSoon(); }
    };

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("receiver", MODE_PRIVATE);
    }
    static synchronized String deviceId(Context context) {
        SharedPreferences p = preferences(context);
        String id = p.getString("uuid", null);
        if (id == null) { id = "uuid:" + UUID.randomUUID(); p.edit().putString("uuid", id).commit(); }
        return id;
    }
    static String deviceName(Context context) {
        String id = deviceId(context);
        return preferences(context).getString("name", "大屏投屏 · " + id.substring(id.length() - 4).toUpperCase(java.util.Locale.US));
    }
    static ReceiverStatus currentStatus() { return status; }
    static void refreshVolume(Context context) {
        AudioManager audio = (AudioManager) context.getSystemService(AUDIO_SERVICE);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int actual = Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / Math.max(1, max));
        PlaybackState.Snapshot s = PlaybackState.INSTANCE.snapshot();
        boolean muted = (actual == 0 && s.muted) || (Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC));
        PlaybackState.INSTANCE.volume(muted && actual == 0 ? s.volume : actual, muted);
    }
    static void ensureStarted(Context context) {
        Intent intent = new Intent(context, ReceiverService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
    }

    @Override public void onCreate() {
        super.onCreate();
        destroyed = false;
        foreground();
        worker = Executors.newSingleThreadScheduledExecutor();
        registerReceiver(network, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        worker.scheduleWithFixedDelay(new Runnable() { @Override public void run() { checkNetwork(); } }, 0, 5, TimeUnit.SECONDS);
    }
    private void foreground() {
        NotificationManager notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channel = "casting";
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(new NotificationChannel(channel, "局域网投屏", NotificationManager.IMPORTANCE_LOW));
        Intent home = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent open = PendingIntent.getActivity(this, 0, home, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, channel) : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.stat_sys_upload_done).setContentTitle("大屏投屏")
                .setContentText("独立 DLNA 接收服务 · 点击打开投屏页面").setContentIntent(open).setOngoing(true);
        startForeground(20, builder.build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { checkSoon(); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void checkSoon() {
        if (worker != null && !worker.isShutdown()) worker.execute(new Runnable() { @Override public void run() { checkNetwork(); } });
    }
    private void publish(final ReceiverStatus value) {
        status = value;
        main.post(new Runnable() { @Override public void run() {
            if (!destroyed) sendBroadcast(new Intent(STATUS_CHANGED).setPackage(getPackageName()));
        } });
    }
    private void checkNetwork() {
        if (destroyed) return;
        String name = deviceName(this);
        try {
            refreshVolume(this);
            if (!preferences(this).getBoolean("enabled", true)) {
                stopEndpoints(); publish(new ReceiverStatus(name, "投屏服务已关闭", "菜单键可重新开启", false)); return;
            }
            ConnectivityManager manager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo active = manager.getActiveNetworkInfo();
            if (active == null || !active.isConnected()
                    || !(active.getType() == ConnectivityManager.TYPE_WIFI || active.getType() == ConnectivityManager.TYPE_ETHERNET)) {
                stopEndpoints(); publish(new ReceiverStatus(name, "投屏服务等待网络", "请连接 Wi-Fi 或有线局域网", false)); return;
            }
            InterfaceAddress lan = findLan(active.getType() == ConnectivityManager.TYPE_WIFI);
            if (lan == null) {
                stopEndpoints(); publish(new ReceiverStatus(name, "尚未获取局域网地址", "目前需要 IPv4 网络", false)); return;
            }
            Inet4Address ip = (Inet4Address) lan.getAddress();
            String key = ip.getHostAddress() + "/" + lan.getNetworkPrefixLength() + "/" + name;
            if (key.equals(boundKey) && http != null && http.isRunning() && ssdp != null && ssdp.isRunning()) return;
            stopEndpoints(); publish(ReceiverStatus.starting(name)); acquireLocks();
            DlnaProtocol.Controller controller = new DlnaProtocol.Controller() {
                @Override public void execute(DlnaProtocol.Command command) throws DlnaProtocol.Fault { dispatch(command); }
            };
            http = new DlnaHttpServer(ip, lan.getNetworkPrefixLength(), deviceId(this), name, PlaybackState.INSTANCE,
                    controller, new DlnaHttpServer.Failure() { @Override public void failed() { endpointFailed(); } });
            http.start(0);
            ssdp = new SsdpDiscovery(deviceId(this), http.location(), ip, lan.getNetworkPrefixLength(),
                    new SsdpDiscovery.Failure() { @Override public void failed() { endpointFailed(); } });
            ssdp.start(); boundKey = key;
            if (destroyed) { stopEndpoints(); return; }
            publish(ReceiverStatus.ready(name, "局域网地址 " + ip.getHostAddress()));
            Log.i(TAG, "Independent DLNA ready on " + ip.getHostAddress() + ":" + http.port());
        } catch (Exception error) {
            stopEndpoints(); publish(new ReceiverStatus(name, "投屏服务启动失败", "菜单中重试；检查网络或端口占用", false));
            Log.w(TAG, "Receiver startup failed: " + error.getClass().getSimpleName());
        }
    }
    private void endpointFailed() {
        if (destroyed) return;
        publish(new ReceiverStatus(deviceName(this), "投屏服务正在恢复", "将自动重建局域网连接", false));
        checkSoon();
    }
    private InterfaceAddress findLan(boolean wifi) throws Exception {
        for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            String name = network.getName();
            // Do not advertise through cellular, VPN, Wi-Fi Direct or virtual interfaces.
            if (wifi ? !(name.startsWith("wlan") || name.startsWith("wifi")) : !(name.startsWith("eth") || name.startsWith("en"))) continue;
            if (!network.isUp() || network.isLoopback()) continue;
            for (InterfaceAddress address : network.getInterfaceAddresses()) {
                InetAddress ip = address.getAddress();
                if (ip instanceof Inet4Address && !ip.isLoopbackAddress() && !ip.isLinkLocalAddress()
                        && address.getNetworkPrefixLength() > 0) return address;
            }
        }
        return null;
    }
    private void acquireLocks() {
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) { multicastLock = wifi.createMulticastLock("tvbox-dlna"); multicastLock.setReferenceCounted(false); multicastLock.acquire(); }
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tvbox:receiver"); wakeLock.setReferenceCounted(false); wakeLock.acquire();
    }
    private void stopEndpoints() {
        boundKey = null;
        if (ssdp != null) { ssdp.stop(); ssdp = null; }
        if (http != null) { http.stop(); http = null; }
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); multicastLock = null;
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); wakeLock = null;
    }
    private void dispatch(final DlnaProtocol.Command command) throws DlnaProtocol.Fault {
        final CountDownLatch done = new CountDownLatch(1);
        final DlnaProtocol.Fault[] error = new DlnaProtocol.Fault[1];
        final java.util.concurrent.atomic.AtomicInteger gate = new java.util.concurrent.atomic.AtomicInteger();
        main.post(new Runnable() { @Override public void run() {
            if (!gate.compareAndSet(0, 1)) { done.countDown(); return; }
            try {
                if (destroyed) throw new DlnaProtocol.Fault(701, "Receiver Stopped");
                executeOnMain(command);
            } catch (DlnaProtocol.Fault fault) { error[0] = fault; }
            catch (Exception failure) { error[0] = new DlnaProtocol.Fault(501, "Player Unavailable"); }
            finally { done.countDown(); }
        } });
        try {
            if (!done.await(2500, TimeUnit.MILLISECONDS)) {
                gate.compareAndSet(0, 2); throw new DlnaProtocol.Fault(501, "Player Timeout");
            }
        } catch (InterruptedException interrupted) { gate.compareAndSet(0, 2); Thread.currentThread().interrupt(); throw new DlnaProtocol.Fault(501, "Receiver Stopped"); }
        if (error[0] != null) throw error[0];
    }
    private void executeOnMain(DlnaProtocol.Command command) throws DlnaProtocol.Fault {
        Log.i(TAG, "DLNA command: " + command.action);
        AudioManager audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        if ("SetVolume".equals(command.action) || "SetMute".equals(command.action)) {
            PlaybackState.Snapshot s = PlaybackState.INSTANCE.snapshot();
            int volume = "SetVolume".equals(command.action) ? command.value : s.volume;
            boolean muted = "SetMute".equals(command.action) ? command.value != 0 : s.muted;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, muted ? 0 : Math.round(volume * max / 100f), 0);
            PlaybackState.INSTANCE.volume(volume, muted); return;
        }
        if ("SetAVTransportURI".equals(command.action)) {
            // Foreground-service status alone does not grant permission to launch a background Activity.
            if (!homeVisible && !playerVisible) throw new DlnaProtocol.Fault(701, "Open Receiver Page First");
            Intent player = new Intent(this, PlayerActivity.class).setAction(PlayerActivity.ACTION_SET_MEDIA)
                    .setData(Uri.parse(command.uri)).putExtra("media_title", command.title)
                    .putExtra("media_metadata", command.metadata).putExtra("subtitle_url", command.subtitle)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            long token = PlaybackState.INSTANCE.begin(command.uri, command.title, command.metadata, false);
            player.putExtra("media_generation", token);
            try { startActivity(player); }
            catch (RuntimeException failure) { PlaybackState.INSTANCE.clear(token); throw failure; }
            return;
        }
        PlayerActivity active = PlayerActivity.currentPlayer();
        PlaybackState.Snapshot state = PlaybackState.INSTANCE.snapshot();
        if ("Stop".equals(command.action)) {
            if (active != null) active.remoteCommand("Stop", 0);
            PlaybackState.INSTANCE.clear(state.generation);
            return;
        }
        if (active == null || active.playbackToken() != state.generation) {
            if (!state.uri.isEmpty() && ("Play".equals(command.action) || "Pause".equals(command.action))) {
                PlaybackState.INSTANCE.requestPlay(state.generation, "Play".equals(command.action)); return;
            }
            throw new DlnaProtocol.Fault(701, "Player Not Ready");
        }
        active.remoteCommand(command.action, command.value);
    }
    @Override public void onDestroy() {
        destroyed = true; unregisterReceiver(network);
        if (worker != null) {
            worker.execute(new Runnable() { @Override public void run() { stopEndpoints(); } }); worker.shutdown();
        }
        main.removeCallbacksAndMessages(null);
        status = new ReceiverStatus(deviceName(this), "投屏服务已停止", "重新打开投屏页面可启动", false);
        stopForeground(true); super.onDestroy();
    }
}
