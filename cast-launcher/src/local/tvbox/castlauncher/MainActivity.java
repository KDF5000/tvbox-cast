package local.tvbox.castlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private View standby;
    private NetworkDisplay connection = NetworkDisplay.disconnected();
    private final BroadcastReceiver networkChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            refreshNetwork();
            standby.invalidate();
        }
    };
    private final Handler handler = new Handler();
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            standby.invalidate();
            handler.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try { ReceiverService.ensureStarted(this); }
        catch (RuntimeException unavailable) { android.util.Log.w("TvBoxReceiver", "Service start restricted"); }
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        final Bitmap background = BitmapFactory.decodeResource(getResources(), R.drawable.standby_dusk_v1, options);
        standby = new View(this) {
            private final TvArtwork artwork = new TvArtwork(background);
            private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm", Locale.CHINA);
            private final SimpleDateFormat date = new SimpleDateFormat("M月d日  EEEE", Locale.CHINA);

            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                Date now = new Date();
                canvas.save();
                canvas.scale(getWidth() / 1280f, getHeight() / 720f);
                artwork.standby(canvas, clock.format(now), date.format(now), connection, ReceiverService.currentStatus());
                canvas.restore();
            }
        };
        standby.setContentDescription("投屏待机。菜单键可打开设置。");
        setContentView(standby);
        standby.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showSettings(); } });
    }

    @Override protected void onResume() {
        super.onResume();
        ReceiverService.homeVisible = true;
        refreshNetwork();
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ReceiverService.STATUS_CHANGED);
        registerReceiver(networkChanged, filter);
        handler.post(refresh);
    }

    @Override protected void onPause() {
        ReceiverService.homeVisible = false;
        unregisterReceiver(networkChanged);
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    private void refreshNetwork() {
        ConnectivityManager network = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = network.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) {
            connection = NetworkDisplay.disconnected();
        } else if (info.getType() == ConnectivityManager.TYPE_WIFI) {
            String ssid = null;
            try {
                WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo wifiInfo = wifi == null ? null : wifi.getConnectionInfo();
                if (wifiInfo != null) ssid = wifiInfo.getSSID();
            } catch (SecurityException unavailable) {
                // Some vendor builds withhold SSID; do not substitute an old or guessed name.
            }
            connection = NetworkDisplay.wifi(ssid, info.getExtraInfo());
        } else if (info.getType() == ConnectivityManager.TYPE_ETHERNET) {
            connection = NetworkDisplay.wired();
        } else {
            connection = NetworkDisplay.other();
        }
        ReceiverStatus receiver = ReceiverService.currentStatus();
        standby.setContentDescription("投屏待机。" + connection.label + "。" + receiver.label
                + "。在投屏列表中选择" + receiver.name + "。菜单键打开设置。");
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (event.getRepeatCount() == 0) showSettings();
            return true;
        }
        return super.onKeyDown(code, event);
    }

    private void showSettings() {
        final boolean enabled = ReceiverService.preferences(this).getBoolean("enabled", true);
        new AlertDialog.Builder(this).setTitle("投屏设置")
                .setItems(new String[]{"修改投屏设备名称", "连接 Wi-Fi / 网络设置", "设为默认桌面（开机进入投屏页）",
                        enabled ? "关闭独立 DLNA 服务" : "开启独立 DLNA 服务", "重新检查投屏服务", "诊断与版本信息", "系统设置 / 切回其他桌面", "授权读取 Wi-Fi 名称（可选）"},
                        new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface dialog, int which) {
                            switch (which) {
                                case 0: rename(); break;
                                case 1: openSettings(new Intent(Settings.ACTION_WIFI_SETTINGS)); break;
                                case 2: chooseHome(); break;
                                case 3: ReceiverService.preferences(MainActivity.this).edit().putBoolean("enabled", !enabled).apply();
                                    ReceiverService.ensureStarted(MainActivity.this); break;
                                case 4: ReceiverService.ensureStarted(MainActivity.this); break;
                                case 5: diagnostics(); break;
                                case 6: openSettings(new Intent(Settings.ACTION_SETTINGS)); break;
                                case 7: requestWifiName(); break;
                            }
                        } }).setNegativeButton("返回", null).show();
    }
    private void rename() {
        final EditText input = new EditText(this);
        input.setSingleLine(true); input.setText(ReceiverService.deviceName(this));
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
        new AlertDialog.Builder(this).setTitle("投屏设备名称").setView(input)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    String name = input.getText().toString().replaceAll("[\\p{Cntrl}]+", " ").trim();
                    if (name.isEmpty()) return;
                    ReceiverService.preferences(MainActivity.this).edit().putString("name", name).apply();
                    ReceiverService.ensureStarted(MainActivity.this);
                } }).setNegativeButton("取消", null).show();
    }
    private void chooseHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = (RoleManager) getSystemService(ROLE_SERVICE);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                try { startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1); return; }
                catch (RuntimeException restricted) { }
            }
        }
        openSettings(new Intent(Settings.ACTION_HOME_SETTINGS));
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException unavailable) {
            if (!Settings.ACTION_SETTINGS.equals(intent.getAction())) {
                try { startActivity(new Intent(Settings.ACTION_SETTINGS)); return; } catch (RuntimeException ignored) { }
            }
            new AlertDialog.Builder(this).setTitle("系统没有提供此入口")
                    .setMessage("请使用设备自带设置。部分厂商不允许更换默认桌面；本应用不会禁用原桌面或强制修改系统。")
                    .setPositiveButton("知道了", null).show();
        }
    }
    private void diagnostics() {
        ReceiverStatus receiver = ReceiverService.currentStatus();
        new AlertDialog.Builder(this).setTitle("投屏诊断 · 2.0")
                .setMessage(Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE
                        + "\n\n设备名称：" + receiver.name + "\n" + connection.label + "\n" + receiver.label + "\n" + receiver.detail
                        + "\n\n独立 DLNA 不依赖华为服务，也不需要 Root。请保持投屏首页或播放页在前台。"
                        + "\n不包含独立 AirPlay、屏幕镜像或 Google Cast。服务就绪不代表视频源一定可以播放。")
                .setPositiveButton("返回", null).show();
    }
    private void requestWifiName() {
        if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED) { refreshNetwork(); standby.invalidate(); return; }
        new AlertDialog.Builder(this).setTitle("读取已连接的 Wi-Fi 名称")
                .setMessage("部分 Android 系统要求位置权限才能读取 Wi-Fi 名称。本应用不调用定位接口，也不上传位置。拒绝授权不影响独立 DLNA 投屏；系统位置开关关闭时名称仍可能无法读取。")
                .setPositiveButton("继续授权", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) {
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
                } }).setNegativeButton("暂不授权", null).show();
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        refreshNetwork(); standby.invalidate();
    }
}
