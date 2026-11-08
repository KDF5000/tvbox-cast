package local.tvbox.castlauncher;

/** Network connectivity is intentionally separate from receiver readiness. */
final class ReceiverStatus {
    final String name, label, detail;
    final boolean ready;
    ReceiverStatus(String deviceName, String state, String explanation, boolean online) {
        name = deviceName; label = state; detail = explanation; ready = online;
    }
    static ReceiverStatus starting(String name) { return new ReceiverStatus(name, "投屏服务正在启动", "等待独立 DLNA 接收器", false); }
    static ReceiverStatus ready(String name, String address) { return new ReceiverStatus(name, "独立 DLNA 已就绪", address, true); }
}
