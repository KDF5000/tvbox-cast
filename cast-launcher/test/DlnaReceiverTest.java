package local.tvbox.castlauncher;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Uses real loopback sockets; the fake player is explicit, not a claim of device compatibility. */
public final class DlnaReceiverTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        protocol(); descriptions(); discovery(); httpAndEvents();
        System.out.println("Passed " + checks + " checks: SOAP, real HTTP/GENA callbacks, malformed-input rejection, SSDP packets, state generations and endpoint restart. No Android device used.");
    }
    private static void check(boolean value, String name) { checks++; if (!value) throw new AssertionError(name); }
    private static String args(String... pairs) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < pairs.length; i += 2) out.append(DlnaProtocol.tag(pairs[i], pairs[i + 1]));
        return out.toString();
    }
    private static String body(String service, String action, String content) {
        return "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body><u:"
                + action + " xmlns:u=\"" + service + "\">" + content + "</u:" + action + "></s:Body></s:Envelope>";
    }
    private static DlnaProtocol.Reply invoke(DlnaProtocol p, String service, String action, String content) {
        return p.soap(service, "\"" + service + "#" + action + "\"", body(service, action, content));
    }
    private static void fault(DlnaProtocol.Reply r, int code) {
        check(r.status == 500 && r.body.contains("<errorCode>" + code + "</errorCode>"), "UPnP fault " + code);
    }
    private static void protocol() throws Exception {
        FakePlayer player = new FakePlayer(); DlnaProtocol p = new DlnaProtocol(player.state, player);
        String instance = args("InstanceID", "0");
        check(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", instance).body.contains("NO_MEDIA_PRESENT"), "Initial state");
        fault(invoke(p, DlnaProtocol.AVT, "Play", instance + args("Speed", "1")), 701);
        String metadata = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:sec=\"http://www.sec.co.kr/\"><item><dc:title>测试 &amp; 视频</dc:title><sec:CaptionInfoEx>http://127.0.0.1/sub.vtt</sec:CaptionInfoEx></item></DIDL-Lite>";
        String set = instance + args("CurrentURI", "http://127.0.0.1/movie.mp4?x=1&y=2", "CurrentURIMetaData", metadata);
        check(invoke(p, DlnaProtocol.AVT, "SetAVTransportURI", set).status == 200, "Set media with DIDL and captions");
        check("测试 & 视频".equals(player.last.title) && player.last.subtitle.endsWith("sub.vtt"), "Metadata parsed");
        check("TRANSITIONING".equals(player.state.snapshot().transport), "Acceptance is not fake PLAYING");
        check(invoke(p, DlnaProtocol.AVT, "Play", instance + args("Speed", "1")).status == 200, "Play while preparing accepted");
        check("TRANSITIONING".equals(player.state.snapshot().transport), "Play while preparing stays transitioning");
        player.prepared(90000);
        check(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", instance).body.contains("PLAYING"), "Actual preparation changes state");
        check(invoke(p, DlnaProtocol.AVT, "Pause", instance).status == 200, "Pause");
        check(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", instance).body.contains("PAUSED_PLAYBACK"), "Pause query");
        check(invoke(p, DlnaProtocol.AVT, "Seek", instance + args("Unit", "REL_TIME", "Target", "00:00:12.500")).status == 200, "Seek");
        check(invoke(p, DlnaProtocol.AVT, "GetPositionInfo", instance).body.contains("<RelTime>00:00:12</RelTime>"), "Real progress query");
        check(invoke(p, DlnaProtocol.AVT, "GetMediaInfo", instance).body.contains("&amp;y=2"), "Query XML escaping");
        fault(invoke(p, DlnaProtocol.AVT, "Seek", instance + args("Unit", "REL_TIME", "Target", "00:99:00")), 711);
        fault(invoke(p, DlnaProtocol.AVT, "Seek", instance + args("Unit", "REL_TIME", "Target", "00:02:00")), 711);
        fault(invoke(p, DlnaProtocol.AVT, "Seek", instance + args("Unit", "TRACK_NR", "Target", "1")), 710);
        fault(invoke(p, DlnaProtocol.AVT, "Play", instance + args("Speed", "2")), 717);
        fault(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", args("InstanceID", "1")), 718);
        fault(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", ""), 402);
        fault(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", instance + instance), 402);
        fault(invoke(p, DlnaProtocol.AVT, "SetPlayMode", instance), 401);
        fault(p.soap(DlnaProtocol.AVT, DlnaProtocol.AVT + "#Pause", body(DlnaProtocol.AVT, "Play", instance)), 401);
        fault(p.soap(DlnaProtocol.AVT, DlnaProtocol.AVT + "#Play", "<!DOCTYPE x [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]><x>&secret;</x>"), 402);
        String deep = "<a>";
        for (int i = 0; i < 30; i++) deep += "<a>";
        for (int i = 0; i < 31; i++) deep += "</a>";
        fault(p.soap(DlnaProtocol.AVT, DlnaProtocol.AVT + "#Play", deep), 402);
        check("https://example.com/a.srt".equals(DlnaProtocol.subtitleFromVideoUrl("http://example.com/v.mp4?subtitle=https%3A%2F%2Fexample.com%2Fa.srt")), "URL subtitle fallback");
        check(DlnaProtocol.subtitleFromVideoUrl("http://example.com/v.mp4?subtitle=file%3A%2F%2F%2Fsecret") == null, "File subtitle rejected");
        fault(invoke(p, DlnaProtocol.AVT, "SetAVTransportURI", instance + args("CurrentURI", "file:///sdcard/private.mp4", "CurrentURIMetaData", "")), 714);
        fault(invoke(p, DlnaProtocol.AVT, "SetAVTransportURI", instance + args("CurrentURI", "http://user:password@example.com/a", "CurrentURIMetaData", "")), 714);
        fault(invoke(p, DlnaProtocol.AVT, "SetAVTransportURI", instance + args("CurrentURI", "http://example.com/a", "CurrentURIMetaData", "<foo/>")), 402);
        String master = instance + args("Channel", "Master");
        check(invoke(p, DlnaProtocol.RCS, "SetVolume", master + args("DesiredVolume", "73")).status == 200, "Volume set");
        check(invoke(p, DlnaProtocol.RCS, "GetVolume", master).body.contains("<CurrentVolume>73</CurrentVolume>"), "Volume query");
        check(invoke(p, DlnaProtocol.RCS, "SetMute", master + args("DesiredMute", "true")).status == 200, "Mute set");
        check(invoke(p, DlnaProtocol.RCS, "GetMute", master).body.contains("<CurrentMute>1</CurrentMute>"), "Mute query");
        fault(invoke(p, DlnaProtocol.RCS, "SetVolume", master + args("DesiredVolume", "101")), 402);
        fault(invoke(p, DlnaProtocol.RCS, "SetMute", master + args("DesiredMute", "7")), 402);
        check(invoke(p, DlnaProtocol.CMS, "GetProtocolInfo", "").body.contains("video/mp4"), "Sink capabilities");
        check(invoke(p, DlnaProtocol.CMS, "GetCurrentConnectionIDs", "").status == 200, "Connection IDs");
        check(invoke(p, DlnaProtocol.CMS, "GetCurrentConnectionInfo", args("ConnectionID", "0")).status == 200, "Connection info");
        fault(invoke(p, DlnaProtocol.CMS, "GetCurrentConnectionInfo", args("ConnectionID", "2")), 706);
        check(invoke(p, DlnaProtocol.AVT, "GetDeviceCapabilities", instance).status == 200, "Device capabilities");
        check(invoke(p, DlnaProtocol.AVT, "GetTransportSettings", instance).status == 200, "Transport settings");
        check(invoke(p, DlnaProtocol.AVT, "GetCurrentTransportActions", instance).body.contains("Seek"), "Available actions");
        check(invoke(p, DlnaProtocol.RCS, "ListPresets", instance).body.contains("FactoryDefaults"), "Presets");
        long previous = player.state.snapshot().generation;
        long next = player.state.begin("http://127.0.0.1/live.m3u8", "Live", "");
        player.state.update(previous, "PLAYING", 40000, 50000, false); player.state.clear(previous);
        check(player.state.snapshot().generation == next && player.state.snapshot().position == 0, "Stale player cannot overwrite or clear new media");
        player.state.update(next, "PLAYING", 1000, 0, false);
        fault(invoke(p, DlnaProtocol.AVT, "Seek", instance + args("Unit", "REL_TIME", "Target", "00:00:00")), 710);
        player.state.update(next, "STOPPED", 0, 0, true);
        check(invoke(p, DlnaProtocol.AVT, "GetTransportInfo", instance).body.contains("ERROR_OCCURRED"), "Real playback error reported");
        fault(invoke(p, DlnaProtocol.AVT, "Play", instance + args("Speed", "1")), 701);
        check(invoke(p, DlnaProtocol.AVT, "Stop", instance).status == 200, "Stop");
        check(player.state.snapshot().uri.isEmpty(), "Stop clears session");
    }
    private static void descriptions() throws Exception {
        check(DlnaProtocol.xml(DlnaDescription.device("uuid:test", "客厅 & 电视")).getElementsByTagName("friendlyName").item(0).getTextContent().equals("客厅 & 电视"), "Device name escaped");
        for (String service : new String[]{DlnaProtocol.AVT, DlnaProtocol.RCS, DlnaProtocol.CMS}) {
            Document d = DlnaProtocol.xml(DlnaDescription.scpd(service));
            Set<String> variables = new HashSet<String>();
            NodeList states = d.getElementsByTagName("stateVariable");
            for (int i = 0; i < states.getLength(); i++) variables.add(((Element) states.item(i)).getElementsByTagName("name").item(0).getTextContent());
            NodeList related = d.getElementsByTagName("relatedStateVariable");
            for (int i = 0; i < related.getLength(); i++) check(variables.contains(related.item(i).getTextContent()), "SCPD variable exists");
        }
    }
    private static void discovery() throws Exception {
        String search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + DlnaProtocol.DEVICE + "\r\n\r\n";
        check(DlnaProtocol.DEVICE.equals(SsdpDiscovery.searchTarget(search)), "SSDP discovery parse");
        check(SsdpDiscovery.searchTarget(search.replace("M-SEARCH", "NOTIFY")) == null, "Ignore advertisements");
        check(SsdpDiscovery.searchTarget(search.replace("MX: 2", "MX: -1")) == null, "Reject invalid MX");
        // RFC 5737 documentation addresses; these checks do not contact a real box.
        Inet4Address ip = (Inet4Address) InetAddress.getByName("192.0.2.23");
        SsdpDiscovery ssdp = new SsdpDiscovery("uuid:test", "http://192.0.2.23:1234/description.xml", ip, 24, new SsdpDiscovery.Failure() { public void failed() { } });
        check(ssdp.response(DlnaProtocol.DEVICE).contains("USN: uuid:test::" + DlnaProtocol.DEVICE), "Renderer USN");
        check(ssdp.response("uuid:test").contains("USN: uuid:test\r\n"), "UUID USN");
        check(ssdp.types().size() == 6, "SSDP root, UUID, renderer and services");
        check(SsdpDiscovery.sameSubnet(ip, InetAddress.getByName("192.0.2.9"), 24), "LAN peer accepted");
        check(!SsdpDiscovery.sameSubnet(ip, InetAddress.getByName("198.51.100.9"), 24), "Other subnet rejected");
        check(!SsdpDiscovery.sameSubnet(ip, InetAddress.getByName("8.8.8.8"), 0), "No wildcard subnet");
        Inet4Address loop = (Inet4Address) InetAddress.getByName("127.0.0.1");
        DatagramSocket reservation = new DatagramSocket(0, loop);
        int testPort = reservation.getLocalPort(); reservation.close();
        SsdpDiscovery local = new SsdpDiscovery("uuid:udp-test", "http://127.0.0.1:1234/description.xml", loop, 8,
                new SsdpDiscovery.Failure() { public void failed() { } }, InetAddress.getByName("239.255.77.77"), testPort);
        DatagramSocket sender = new DatagramSocket(0, loop); sender.setSoTimeout(3000);
        try {
            local.start();
            byte[] request = search.getBytes("UTF-8");
            sender.send(new DatagramPacket(request, request.length, loop, testPort));
            byte[] buffer = new byte[4096]; DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
            sender.receive(reply);
            String text = new String(reply.getData(), reply.getOffset(), reply.getLength(), "UTF-8");
            check(text.contains("ST: " + DlnaProtocol.DEVICE) && text.contains("USN: uuid:udp-test::"), "Real SSDP UDP request/response on loopback");
        } finally { sender.close(); local.stop(); }
    }
    private static void httpAndEvents() throws Exception {
        final FakePlayer player = new FakePlayer();
        Inet4Address loop = (Inet4Address) InetAddress.getByName("127.0.0.1");
        DlnaHttpServer server = new DlnaHttpServer(loop, 8, "uuid:socket-test", "测试接收器", player.state, player,
                new DlnaHttpServer.Failure() { public void failed() { throw new AssertionError("Unexpected HTTP failure"); } });
        Callback callback = new Callback();
        int port;
        try {
            server.start(0); port = server.port();
            check(exchange(port, "GET", "/description.xml", "", "").body.contains("测试接收器"), "HTTP description");
            check(exchange(port, "HEAD", "/description.xml", "", "").body.isEmpty(), "HEAD has no body");
            check(exchange(port, "GET", "/avt.xml", "", "").status == 200, "HTTP SCPD");
            check(exchange(port, "GET", "/unknown", "", "").status == 404, "HTTP missing route");
            String instance = args("InstanceID", "0");
            check(exchange(port, "POST", "/control/avt", "SOAPACTION: \"" + DlnaProtocol.AVT + "#GetTransportInfo\"\r\n", body(DlnaProtocol.AVT, "GetTransportInfo", instance)).body.contains("NO_MEDIA_PRESENT"), "Real SOAP over HTTP");
            check(raw(port, "POST /control/avt HTTP/1.1\r\nContent-Length: 65537\r\n\r\n").status == 413, "Body size limit");
            check(raw(port, "POST /control/avt HTTP/1.1\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\n").status == 400, "Duplicate lengths rejected");
            check(raw(port, "POST /control/avt HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n").status == 501, "Chunked framing rejected");
            check(raw(port, "POST /control/avt HTTP/1.1\r\n\r\n").status == 411, "Missing SOAP length rejected");
            Response subscribed = exchange(port, "SUBSCRIBE", "/event/avt", "CALLBACK: <http://127.0.0.1:" + callback.port() + "/events>\r\nNT: upnp:event\r\nTIMEOUT: Second-120\r\n", "");
            String sid = subscribed.headers.get("sid");
            check(subscribed.status == 200 && sid.startsWith("uuid:"), "GENA subscribe");
            callback.await(1);
            check(callback.events.get(0).headers.get("seq").equals("0"), "Initial SEQ zero");
            check(callback.events.get(0).body.contains("NO_MEDIA_PRESENT"), "Initial state event");
            long token = player.state.begin("http://127.0.0.1/a.mp4", "A & B", "");
            player.state.update(token, "PLAYING", 1200, 50000, false);
            callback.await(2);
            check(callback.events.get(1).body.contains("PLAYING") && "1".equals(callback.events.get(1).headers.get("seq")), "Changed playback notification");
            player.state.update(token, "PAUSED_PLAYBACK", 1200, 50000, false);
            callback.await(3);
            check(callback.events.get(2).body.contains("PAUSED_PLAYBACK"), "Pause event");
            check(exchange(port, "SUBSCRIBE", "/event/avt", "SID: " + sid + "\r\nTIMEOUT: Second-60\r\n", "").status == 200, "GENA renew");
            check(exchange(port, "SUBSCRIBE", "/event/rcs", "SID: " + sid + "\r\n", "").status == 412, "Subscription bound to service");
            check(exchange(port, "SUBSCRIBE", "/event/avt", "CALLBACK: <http://127.0.0.2:1234/events>\r\nNT: upnp:event\r\n", "").status == 412, "Foreign callback rejected");
            check(exchange(port, "SUBSCRIBE", "/event/avt", "CALLBACK: <http://localhost:1234/events>\r\nNT: upnp:event\r\n", "").status == 412, "DNS callback rejected");
            check(exchange(port, "UNSUBSCRIBE", "/event/avt", "SID: " + sid + "\r\n", "").status == 200, "GENA unsubscribe");
            check(exchange(port, "SUBSCRIBE", "/event/avt", "SID: " + sid + "\r\n", "").status == 412, "Removed SID rejected");
            server.stop();
            check(!server.isRunning(), "Endpoint stops");
            server.start(port);
            check(exchange(port, "GET", "/description.xml", "", "").status == 200, "Endpoint restart rebinds port");
        } finally { server.stop(); callback.close(); }
    }
    private static Response exchange(int port, String method, String path, String headers, String body) throws Exception {
        return raw(port, method + " " + path + " HTTP/1.1\r\nHOST: 127.0.0.1:" + port + "\r\n" + headers
                + "Content-Length: " + body.getBytes("UTF-8").length + "\r\nConnection: close\r\n\r\n" + body);
    }
    private static Response raw(int port, String request) throws Exception {
        Socket socket = new Socket("127.0.0.1", port); socket.setSoTimeout(4000);
        try {
            OutputStream output = socket.getOutputStream(); output.write(request.getBytes("UTF-8")); output.flush();
            return read(socket.getInputStream(), request.startsWith("HEAD "));
        } finally { socket.close(); }
    }
    private static Response read(InputStream raw, boolean head) throws Exception {
        InputStream input = new BufferedInputStream(raw);
        Response result = new Response(); result.start = line(input);
        if (result.start.startsWith("HTTP/")) result.status = Integer.parseInt(result.start.split(" ")[1]);
        String line;
        while (!(line = line(input)).isEmpty()) { int colon = line.indexOf(':'); result.headers.put(line.substring(0, colon).toLowerCase(Locale.US), line.substring(colon + 1).trim()); }
        int length = head ? 0 : Integer.parseInt(result.headers.get("content-length"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < length; i++) { int value = input.read(); if (value < 0) throw new AssertionError("Truncated HTTP"); out.write(value); }
        result.body = new String(out.toByteArray(), "UTF-8"); return result;
    }
    private static String line(InputStream input) throws Exception {
        StringBuilder s = new StringBuilder();
        for (int v; (v = input.read()) >= 0;) { if (v == '\n') return s.toString().replace("\r", ""); s.append((char) v); }
        throw new AssertionError("Incomplete headers");
    }
    private static final class Response {
        String start, body; int status;
        final Map<String, String> headers = new HashMap<String, String>();
    }
    private static final class Callback {
        final ServerSocket server = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1"));
        final List<Response> events = Collections.synchronizedList(new ArrayList<Response>());
        volatile Throwable failure;
        Callback() throws Exception {
            Thread listener = new Thread(new Runnable() { @Override public void run() {
                while (!server.isClosed()) {
                    try {
                        Socket socket = server.accept(); socket.setSoTimeout(3000);
                        try {
                            Response event = read(socket.getInputStream(), false);
                            socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n".getBytes("UTF-8"));
                            socket.getOutputStream().flush(); events.add(event);
                        } finally { socket.close(); }
                    } catch (Throwable error) { if (!server.isClosed()) failure = error; return; }
                }
            } }, "test-callback"); listener.setDaemon(true); listener.start();
        }
        int port() { return server.getLocalPort(); }
        void await(int count) throws Exception {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (events.size() < count && failure == null && System.nanoTime() < end) Thread.sleep(20);
            check(failure == null && events.size() >= count, "GENA callback arrived " + count);
        }
        void close() throws Exception { server.close(); }
    }
    private static final class FakePlayer implements DlnaProtocol.Controller {
        final PlaybackState state = new PlaybackState(); DlnaProtocol.Command last; boolean ready;
        public void execute(DlnaProtocol.Command command) {
            last = command; PlaybackState.Snapshot s = state.snapshot();
            if ("SetAVTransportURI".equals(command.action)) { ready = false; state.begin(command.uri, command.title, command.metadata, false); }
            else if ("Play".equals(command.action) || "Pause".equals(command.action)) {
                boolean play = "Play".equals(command.action); state.requestPlay(s.generation, play);
                if (ready) state.update(s.generation, play ? "PLAYING" : "PAUSED_PLAYBACK", s.position, s.duration, false);
            } else if ("Seek".equals(command.action)) state.update(s.generation, s.transport, command.value, s.duration, false);
            else if ("Stop".equals(command.action)) state.clear(s.generation);
            else if ("SetVolume".equals(command.action)) state.volume(command.value, s.muted);
            else if ("SetMute".equals(command.action)) state.volume(s.volume, command.value != 0);
        }
        void prepared(int duration) { ready = true; PlaybackState.Snapshot s = state.snapshot(); state.update(s.generation, s.playRequested ? "PLAYING" : "STOPPED", 0, duration, false); }
    }
}
