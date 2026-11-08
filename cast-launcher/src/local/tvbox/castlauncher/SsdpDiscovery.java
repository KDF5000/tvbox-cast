package local.tvbox.castlauncher;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** SSDP discovery on the chosen LAN interface, not on VPNs or every interface. */
final class SsdpDiscovery {
    interface Failure { void failed(); }
    private final String uuid, location;
    private final Inet4Address address;
    private final int prefix;
    private final Failure failure;
    private final InetAddress group;
    private final int discoveryPort;
    private MulticastSocket socket;
    private ScheduledExecutorService timer;
    private volatile boolean running;
    private int queuedSearches;
    SsdpDiscovery(String id, String url, Inet4Address ip, int mask, Failure f) {
        this(id, url, ip, mask, f, multicast(), 1900);
    }
    // Test-only injection uses an isolated multicast group on loopback, never the user's LAN.
    SsdpDiscovery(String id, String url, Inet4Address ip, int mask, Failure f, InetAddress multicastGroup, int port) {
        uuid = id; location = url; address = ip; prefix = mask; failure = f;
        group = multicastGroup; discoveryPort = port;
    }
    List<String> types() { return Arrays.asList("upnp:rootdevice", uuid, DlnaProtocol.DEVICE, DlnaProtocol.AVT, DlnaProtocol.RCS, DlnaProtocol.CMS); }
    void start() throws IOException {
        socket = new MulticastSocket(null);
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(discoveryPort));
            NetworkInterface network = NetworkInterface.getByInetAddress(address);
            if (network == null) throw new IOException("LAN interface missing");
            socket.setNetworkInterface(network);
            socket.setTimeToLive(2);
            socket.joinGroup(new InetSocketAddress(group, discoveryPort), network);
            timer = Executors.newSingleThreadScheduledExecutor();
            running = true;
            Thread listener = new Thread(new Runnable() { @Override public void run() { listen(); } }, "cast-ssdp");
            listener.setDaemon(true); listener.start();
            timer.scheduleAtFixedRate(new Runnable() { @Override public void run() { announce("ssdp:alive"); } }, 0, 60, TimeUnit.SECONDS);
        } catch (IOException error) { socket.close(); throw error; }
    }
    boolean isRunning() { return running && socket != null && !socket.isClosed(); }
    private void listen() {
        byte[] data = new byte[8192];
        try {
            while (running) {
                DatagramPacket packet = new DatagramPacket(data, data.length);
                socket.receive(packet);
                if (!sameSubnet(address, packet.getAddress(), prefix)) continue;
                String st = searchTarget(new String(packet.getData(), packet.getOffset(), packet.getLength(), "UTF-8"));
                if (st == null || (!"ssdp:all".equals(st) && !types().contains(st))) continue;
                final InetAddress peer = packet.getAddress(); final int port = packet.getPort(); final String target = st;
                synchronized (this) { if (queuedSearches >= 32) continue; queuedSearches++; }
                timer.schedule(new Runnable() { @Override public void run() {
                    try {
                        if (!running) return;
                        for (String type : types()) if ("ssdp:all".equals(target) || type.equals(target)) send(response(type), peer, port);
                    } finally { synchronized (SsdpDiscovery.this) { queuedSearches--; } }
                } }, (long) (Math.random() * 400), TimeUnit.MILLISECONDS);
            }
        } catch (Exception error) {
            if (running) { running = false; failure.failed(); }
        }
    }
    static String searchTarget(String request) {
        String[] lines = request.split("\r?\n");
        if (lines.length < 2 || !"M-SEARCH * HTTP/1.1".equals(lines[0])) return null;
        String st = null, man = null, mx = null, host = null;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':'); if (colon < 0) continue;
            String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.US);
            String value = lines[i].substring(colon + 1).trim();
            if ("st".equals(name)) st = value;
            if ("man".equals(name)) man = value;
            if ("mx".equals(name)) mx = value;
            if ("host".equals(name)) host = value;
        }
        try { if (Integer.parseInt(mx) < 1) return null; } catch (Exception invalid) { return null; }
        return "\"ssdp:discover\"".equalsIgnoreCase(man) && "239.255.255.250:1900".equals(host) ? st : null;
    }
    String response(String type) {
        return "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=180\r\nEXT:\r\nLOCATION: " + location
                + "\r\nSERVER: Android UPnP/1.0 TVBox/2.0\r\nST: " + type + "\r\nUSN: " + usn(type) + "\r\n\r\n";
    }
    private String usn(String type) { return uuid.equals(type) ? uuid : uuid + "::" + type; }
    private void announce(String action) {
        for (String type : types()) send("NOTIFY * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nNT: " + type
                + "\r\nNTS: " + action + "\r\nUSN: " + usn(type)
                + ("ssdp:alive".equals(action) ? "\r\nCACHE-CONTROL: max-age=180\r\nLOCATION: " + location + "\r\nSERVER: Android UPnP/1.0 TVBox/2.0" : "")
                + "\r\n\r\n", group, discoveryPort);
    }
    private static InetAddress multicast() { try { return InetAddress.getByName("239.255.255.250"); } catch (Exception impossible) { throw new IllegalStateException(impossible); } }
    private synchronized void send(String text, InetAddress peer, int port) {
        try {
            byte[] bytes = text.getBytes("UTF-8");
            socket.send(new DatagramPacket(bytes, bytes.length, peer, port));
        } catch (IOException ignored) { }
    }
    void stop() {
        if (socket != null && !socket.isClosed()) announce("ssdp:byebye");
        running = false;
        if (socket != null) socket.close();
        if (timer != null) timer.shutdownNow();
    }
    static boolean sameSubnet(InetAddress local, InetAddress peer, int prefix) {
        if (!(local instanceof Inet4Address) || !(peer instanceof Inet4Address) || prefix < 1 || prefix > 32) return false;
        byte[] a = local.getAddress(), b = peer.getAddress();
        for (int i = 0; i < 4; i++) {
            int bits = Math.min(8, Math.max(0, prefix - i * 8));
            int mask = (0xff << (8 - bits)) & 0xff;
            if ((a[i] & mask) != (b[i] & mask)) return false;
        }
        return true;
    }
}
