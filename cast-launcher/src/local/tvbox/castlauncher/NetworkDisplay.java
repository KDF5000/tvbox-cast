package local.tvbox.castlauncher;

/** Connection labels shared by the Android view and offline artwork previews. */
final class NetworkDisplay {
    final boolean connected;
    final boolean wifi;
    final String ssid;
    final String label;
    final String instruction;

    private NetworkDisplay(boolean connected, boolean wifi, String ssid, String label, String instruction) {
        this.connected = connected;
        this.wifi = wifi;
        this.ssid = ssid;
        this.label = label;
        this.instruction = instruction;
    }

    static NetworkDisplay disconnected() {
        return new NetworkDisplay(false, false, null, "等待网络连接", "请先将盒子连接到家庭网络。");
    }

    static NetworkDisplay wifi(String rawSsid, String fallbackSsid) {
        String ssid = cleanSsid(rawSsid);
        if (ssid == null) ssid = cleanSsid(fallbackSsid);
        return new NetworkDisplay(true, true, ssid,
                ssid == null ? "Wi-Fi 已连接 · 名称暂不可用" : "Wi-Fi 已连接 · " + ssid,
                "手机连接同一 Wi-Fi，打开视频投屏。");
    }

    static NetworkDisplay wired() {
        return new NetworkDisplay(true, false, null, "有线网络已连接", "手机连接同一路由器的 Wi-Fi，再投屏。");
    }

    static NetworkDisplay other() {
        return new NetworkDisplay(true, false, null, "网络已连接", "手机与盒子连接同一局域网，再投屏。");
    }

    static String cleanSsid(String value) {
        if (value == null) return null;
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.trim().isEmpty() || "<unknown ssid>".equalsIgnoreCase(value)
                || "0x".equalsIgnoreCase(value)) return null;
        return value;
    }
}
