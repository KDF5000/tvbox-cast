package local.tvbox.castlauncher;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Bounded HTTP/SOAP/GENA endpoint, restricted to peers on the selected IPv4 subnet. */
final class DlnaHttpServer {
    interface Failure { void failed(); }
    private final Inet4Address address;
    private final int prefix;
    private final String uuid, name;
    private final PlaybackState state;
    private final DlnaProtocol protocol;
    private final Failure failure;
    private final Set<Socket> clients = Collections.synchronizedSet(new HashSet<Socket>());
    private final Map<String, Subscription> subscriptions = new HashMap<String, Subscription>();
    private ServerSocket server;
    private ThreadPoolExecutor requests, notifications;
    private ScheduledExecutorService timer;
    private volatile boolean running;
    DlnaHttpServer(Inet4Address ip, int mask, String id, String title, PlaybackState s,
            DlnaProtocol.Controller control, Failure f) {
        address = ip; prefix = mask; uuid = id; name = title; state = s;
        protocol = new DlnaProtocol(s, control); failure = f;
    }
    void start(int port) throws IOException {
        server = new ServerSocket();
        try {
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(address, port), 16);
            requests = pool(4, 16); notifications = pool(2, 16);
            timer = Executors.newSingleThreadScheduledExecutor();
            running = true;
            Thread thread = new Thread(new Runnable() { @Override public void run() { accept(); } }, "cast-http");
            thread.setDaemon(true); thread.start();
            timer.scheduleAtFixedRate(new Runnable() { @Override public void run() { notifyChanges(); } }, 1, 1, TimeUnit.SECONDS);
        } catch (IOException error) { server.close(); throw error; }
    }
    private static ThreadPoolExecutor pool(int threads, int queue) {
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queue));
    }
    int port() { return server.getLocalPort(); }
    String location() { return "http://" + address.getHostAddress() + ":" + port() + "/description.xml"; }
    boolean isRunning() { return running && server != null && !server.isClosed(); }
    private void accept() {
        final ServerSocket owner = server;
        try {
            while (running && server == owner) {
                final Socket client = owner.accept();
                if (!SsdpDiscovery.sameSubnet(address, client.getInetAddress(), prefix)) { client.close(); continue; }
                client.setSoTimeout(3000); clients.add(client);
                try { requests.execute(new Runnable() { @Override public void run() { serve(client, owner); } }); }
                catch (RejectedExecutionException full) { clients.remove(client); client.close(); }
            }
        } catch (IOException error) { if (running && server == owner) { running = false; failure.failed(); } }
    }
    private void serve(Socket client, ServerSocket owner) {
        try {
            InputStream input = new BufferedInputStream(client.getInputStream());
            OutputStream output = client.getOutputStream();
            String line = line(input, 2048);
            String[] first = line.split(" ");
            if (first.length != 3 || !("HTTP/1.1".equals(first[2]) || "HTTP/1.0".equals(first[2]))) throw new HttpError(400);
            Map<String, String> headers = new HashMap<String, String>();
            int headerBytes = 0;
            while (!(line = line(input, 8192)).isEmpty()) {
                headerBytes += line.length(); if (headerBytes > 8192) throw new HttpError(431);
                int colon = line.indexOf(':'); if (colon < 1) throw new HttpError(400);
                String key = line.substring(0, colon).toLowerCase(Locale.US);
                if (!key.matches("[a-z0-9-]+") || headers.put(key, line.substring(colon + 1).trim()) != null) throw new HttpError(400);
            }
            if (headers.containsKey("transfer-encoding")) throw new HttpError(501);
            int length = 0;
            try { if (headers.containsKey("content-length")) length = Integer.parseInt(headers.get("content-length")); }
            catch (NumberFormatException invalid) { throw new HttpError(400); }
            if (length < 0) throw new HttpError(400);
            if (length > 65536) throw new HttpError(413);
            if ("POST".equals(first[0]) && !headers.containsKey("content-length")) throw new HttpError(411);
            if (headers.containsKey("expect")) {
                if (!"100-continue".equalsIgnoreCase(headers.get("expect"))) throw new HttpError(417);
                output.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes("UTF-8")); output.flush();
            }
            byte[] bytes = new byte[length];
            for (int read = 0; read < length;) {
                int count = input.read(bytes, read, length - read); if (count < 0) throw new HttpError(400); read += count;
            }
            if (!running || server != owner) return;
            Response response = route(first[0], first[1], headers, new String(bytes, "UTF-8"), client.getInetAddress());
            respond(output, response, "HEAD".equals(first[0]));
            if (response.after != null && running) response.after.run();
        } catch (HttpError error) {
            try { respond(client.getOutputStream(), new Response(error.code, ""), false); } catch (IOException ignored) { }
        } catch (Exception ignored) {
            // Network errors and malformed requests never print media URLs or request bodies.
        } finally { clients.remove(client); try { client.close(); } catch (IOException ignored) { } }
    }
    private Response route(String method, String path, Map<String, String> headers, String body, InetAddress peer) throws HttpError {
        if ("GET".equals(method) || "HEAD".equals(method)) {
            if ("/description.xml".equals(path)) return new Response(200, DlnaDescription.device(uuid, name));
            if ("/avt.xml".equals(path)) return new Response(200, DlnaDescription.scpd(DlnaProtocol.AVT));
            if ("/rcs.xml".equals(path)) return new Response(200, DlnaDescription.scpd(DlnaProtocol.RCS));
            if ("/cms.xml".equals(path)) return new Response(200, DlnaDescription.scpd(DlnaProtocol.CMS));
            if ("/".equals(path)) {
                Response r = new Response(200, "<!doctype html><meta charset=\"utf-8\"><title>大屏投屏</title>"
                        + "<h1>" + DlnaProtocol.escape(name) + "</h1><p>DLNA 视频接收器。请在同一局域网的播放器中选择此设备。</p>");
                r.type = "text/html; charset=utf-8"; return r;
            }
            throw new HttpError(404);
        }
        String service = service(path);
        if ("POST".equals(method) && path.startsWith("/control/") && service != null) {
            DlnaProtocol.Reply reply = protocol.soap(service, headers.get("soapaction"), body);
            Response response = new Response(reply.status, reply.body); response.headers.put("EXT", ""); return response;
        }
        if (path.startsWith("/event/") && service != null) {
            if ("SUBSCRIBE".equals(method)) return subscribe(path, service, headers, peer);
            if ("UNSUBSCRIBE".equals(method)) {
                if (headers.containsKey("callback") || headers.containsKey("nt")) throw new HttpError(400);
                synchronized (subscriptions) {
                    Subscription s = owned(headers.get("sid"), peer, path); subscriptions.remove(s.sid);
                }
                return new Response(200, "");
            }
        }
        throw new HttpError(service == null ? 404 : 405);
    }
    private static String service(String path) {
        if ("/control/avt".equals(path) || "/event/avt".equals(path)) return DlnaProtocol.AVT;
        if ("/control/rcs".equals(path) || "/event/rcs".equals(path)) return DlnaProtocol.RCS;
        if ("/control/cms".equals(path) || "/event/cms".equals(path)) return DlnaProtocol.CMS;
        return null;
    }
    private Response subscribe(String path, String service, Map<String, String> headers, InetAddress peer) throws HttpError {
        int timeout = 1800;
        String rawTimeout = headers.get("timeout");
        if (rawTimeout != null && !"Second-infinite".equalsIgnoreCase(rawTimeout)) {
            try {
                if (!rawTimeout.startsWith("Second-")) throw new Exception();
                timeout = Math.min(1800, Integer.parseInt(rawTimeout.substring(7)));
                if (timeout < 1) throw new Exception();
            } catch (Exception invalid) { throw new HttpError(400); }
        }
        final Subscription subscription;
        synchronized (subscriptions) {
            prune();
            if (headers.containsKey("sid")) {
                if (headers.containsKey("callback") || headers.containsKey("nt")) throw new HttpError(400);
                subscription = owned(headers.get("sid"), peer, path);
            } else {
                if (!"upnp:event".equals(headers.get("nt"))) throw new HttpError(412);
                if (subscriptions.size() >= 16) throw new HttpError(503);
                URI callback;
                try {
                    String value = headers.get("callback");
                    if (value == null || !value.startsWith("<") || !value.endsWith(">")) throw new Exception();
                    callback = new URI(value.substring(1, value.length() - 1));
                    // Numeric IP must match subscriber. Connect directly to that IP; no DNS or redirects.
                    if (!"http".equalsIgnoreCase(callback.getScheme()) || !peer.getHostAddress().equals(callback.getHost())
                            || callback.getUserInfo() != null || callback.getFragment() != null || callback.getPort() == 0
                            || callback.getPort() > 65535 || value.length() > 2048) throw new Exception();
                } catch (Exception invalid) { throw new HttpError(412); }
                subscription = new Subscription("uuid:" + UUID.randomUUID(), service, path, callback, peer);
                subscriptions.put(subscription.sid, subscription);
            }
            subscription.expires = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        }
        Response response = new Response(200, ""); response.headers.put("SID", subscription.sid);
        response.headers.put("TIMEOUT", "Second-" + timeout);
        response.after = new Runnable() { @Override public void run() { queueNotification(subscription); } };
        return response;
    }
    private Subscription owned(String sid, InetAddress peer, String path) throws HttpError {
        Subscription s = subscriptions.get(sid);
        if (s == null || s.expires <= System.nanoTime() || !s.peer.equals(peer) || !s.path.equals(path)) throw new HttpError(412);
        return s;
    }
    private void prune() {
        Iterator<Subscription> i = subscriptions.values().iterator();
        while (i.hasNext()) if (i.next().expires <= System.nanoTime()) i.remove();
    }
    private void notifyChanges() {
        List<Subscription> list;
        synchronized (subscriptions) { prune(); list = new ArrayList<Subscription>(subscriptions.values()); }
        for (Subscription s : list) queueNotification(s);
    }
    private void queueNotification(final Subscription s) {
        final String body = event(s.service, state.snapshot());
        synchronized (subscriptions) {
            if (!running || subscriptions.get(s.sid) != s || s.busy || body.equals(s.lastBody)) return;
            s.busy = true;
        }
        try { notifications.execute(new Runnable() { @Override public void run() { DlnaHttpServer.this.notify(s, body); } }); }
        catch (RejectedExecutionException full) { synchronized (subscriptions) { s.busy = false; } }
    }
    private void notify(Subscription s, String body) {
        boolean success = false;
        Socket callback = new Socket(); clients.add(callback);
        try {
            int port = s.callback.getPort() < 0 ? 80 : s.callback.getPort();
            callback.connect(new InetSocketAddress(s.peer, port), 2000); callback.setSoTimeout(2000);
            String path = s.callback.getRawPath(); if (path == null || path.isEmpty()) path = "/";
            if (s.callback.getRawQuery() != null) path += "?" + s.callback.getRawQuery();
            byte[] bytes = body.getBytes("UTF-8");
            OutputStream output = callback.getOutputStream();
            output.write(("NOTIFY " + path + " HTTP/1.1\r\nHOST: " + s.peer.getHostAddress() + ":" + port
                    + "\r\nCONTENT-TYPE: text/xml; charset=utf-8\r\nNT: upnp:event\r\nNTS: upnp:propchange\r\nSID: "
                    + s.sid + "\r\nSEQ: " + s.sequence + "\r\nCONTENT-LENGTH: " + bytes.length + "\r\nCONNECTION: close\r\n\r\n").getBytes("UTF-8"));
            output.write(bytes); output.flush();
            success = line(callback.getInputStream(), 1024).matches("HTTP/1\\.[01] 2\\d\\d.*");
        } catch (Exception ignored) { }
        finally { clients.remove(callback); try { callback.close(); } catch (IOException ignored) { } }
        synchronized (subscriptions) {
            s.busy = false;
            if (success) { s.lastBody = body; s.failures = 0; s.sequence = s.sequence == 0xffffffffL ? 1 : s.sequence + 1; }
            else if (++s.failures >= 3) subscriptions.remove(s.sid);
        }
    }
    static String event(String service, PlaybackState.Snapshot s) {
        String props;
        if (DlnaProtocol.CMS.equals(service)) {
            props = "<e:property><SourceProtocolInfo/></e:property><e:property>"
                    + DlnaProtocol.tag("SinkProtocolInfo", DlnaProtocol.SINK) + "</e:property><e:property><CurrentConnectionIDs>0</CurrentConnectionIDs></e:property>";
        } else {
            String inner = "<Event xmlns=\"urn:schemas-upnp-org:metadata-1-0/" + (DlnaProtocol.AVT.equals(service) ? "AVT" : "RCS") + "/\"><InstanceID val=\"0\">";
            if (DlnaProtocol.AVT.equals(service)) inner += val("TransportState", s.transport) + val("TransportStatus", s.status)
                    + val("TransportPlaySpeed", "1") + val("AVTransportURI", s.uri) + val("AVTransportURIMetaData", s.metadata)
                    + val("CurrentTrackURI", s.uri) + val("CurrentTrackMetaData", s.metadata)
                    + val("CurrentTrackDuration", DlnaProtocol.time(s.duration)) + val("NumberOfTracks", s.uri.isEmpty() ? "0" : "1")
                    + val("CurrentTrack", s.uri.isEmpty() ? "0" : "1")
                    + val("CurrentTransportActions", DlnaProtocol.transportActions(s));
            else inner += "<Volume channel=\"Master\" val=\"" + s.volume + "\"/><Mute channel=\"Master\" val=\"" + (s.muted ? "1" : "0") + "\"/>";
            inner += "</InstanceID></Event>";
            props = "<e:property>" + DlnaProtocol.tag("LastChange", inner) + "</e:property>";
        }
        return "<?xml version=\"1.0\"?><e:propertyset xmlns:e=\"urn:schemas-upnp-org:event-1-0\">" + props + "</e:propertyset>";
    }
    private static String val(String name, String value) { return "<" + name + " val=\"" + DlnaProtocol.escape(value) + "\"/>"; }
    private static String line(InputStream input, int limit) throws IOException, HttpError {
        StringBuilder text = new StringBuilder();
        for (int value; (value = input.read()) != -1;) {
            if (value == '\n') return text.length() > 0 && text.charAt(text.length() - 1) == '\r'
                    ? text.substring(0, text.length() - 1) : text.toString();
            if (text.length() >= limit || value == 0) throw new HttpError(400);
            text.append((char) value);
        }
        throw new IOException("Incomplete request");
    }
    private static void respond(OutputStream output, Response response, boolean head) throws IOException {
        byte[] bytes = response.body.getBytes("UTF-8");
        StringBuilder headers = new StringBuilder("HTTP/1.1 " + response.status + " " + reason(response.status) + "\r\n");
        headers.append("CONTENT-LENGTH: ").append(bytes.length).append("\r\nCONTENT-TYPE: ").append(response.type)
                .append("\r\nSERVER: Android UPnP/1.0 TVBox/2.0\r\nCONNECTION: close\r\n");
        for (Map.Entry<String, String> h : response.headers.entrySet()) headers.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        headers.append("\r\n"); output.write(headers.toString().getBytes("UTF-8"));
        if (!head) output.write(bytes); output.flush();
    }
    private static String reason(int status) {
        switch (status) {
            case 200: return "OK"; case 400: return "Bad Request"; case 404: return "Not Found";
            case 405: return "Method Not Allowed"; case 411: return "Length Required"; case 412: return "Precondition Failed";
            case 413: return "Payload Too Large"; case 417: return "Expectation Failed"; case 431: return "Request Header Fields Too Large";
            case 501: return "Not Implemented"; case 503: return "Service Unavailable"; default: return "Internal Server Error";
        }
    }
    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (IOException ignored) { }
        if (timer != null) timer.shutdownNow();
        if (requests != null) requests.shutdownNow();
        if (notifications != null) notifications.shutdownNow();
        synchronized (clients) { for (Socket s : clients) try { s.close(); } catch (IOException ignored) { } clients.clear(); }
        synchronized (subscriptions) { subscriptions.clear(); }
    }
    private static final class HttpError extends Exception { final int code; HttpError(int c) { code = c; } }
    private static final class Response {
        final int status; final String body; String type = "text/xml; charset=utf-8";
        final Map<String, String> headers = new HashMap<String, String>(); Runnable after;
        Response(int code, String text) { status = code; body = text; }
    }
    private static final class Subscription {
        final String sid, service, path; final URI callback; final InetAddress peer;
        long expires, sequence; int failures; boolean busy; String lastBody;
        Subscription(String id, String type, String p, URI url, InetAddress ip) { sid = id; service = type; path = p; callback = url; peer = ip; }
    }
}
