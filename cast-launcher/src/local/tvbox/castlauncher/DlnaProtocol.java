package local.tvbox.castlauncher;

import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/** Standard UPnP MediaRenderer control plane. No Huawei classes or Android APIs. */
final class DlnaProtocol {
    static final String AVT = "urn:schemas-upnp-org:service:AVTransport:1";
    static final String RCS = "urn:schemas-upnp-org:service:RenderingControl:1";
    static final String CMS = "urn:schemas-upnp-org:service:ConnectionManager:1";
    static final String DEVICE = "urn:schemas-upnp-org:device:MediaRenderer:1";
    static final String SINK = "http-get:*:video/mp4:*,http-get:*:video/mpeg:*,"
            + "http-get:*:application/vnd.apple.mpegurl:*,http-get:*:application/x-mpegURL:*";
    interface Controller { void execute(Command command) throws Fault; }
    static final class Command {
        final String action, uri, title, metadata, subtitle;
        final int value;
        Command(String a, String u, String t, String m, String s, int v) {
            action = a; uri = u; title = t; metadata = m; subtitle = s; value = v;
        }
        Command(String a, int v) { this(a, null, null, null, null, v); }
    }
    static final class Fault extends Exception {
        final int code;
        Fault(int c, String description) { super(description); code = c; }
    }
    static final class Reply {
        final int status;
        final String body;
        Reply(int s, String b) { status = s; body = b; }
    }
    private final PlaybackState state;
    private final Controller controller;
    DlnaProtocol(PlaybackState s, Controller c) { state = s; controller = c; }

    Reply soap(String service, String soapAction, String body) {
        try {
            if (!AVT.equals(service) && !RCS.equals(service) && !CMS.equals(service)) {
                throw new Fault(401, "Invalid Action");
            }
            Document document = xml(body);
            Element envelope = document.getDocumentElement();
            if (!"Envelope".equals(envelope.getLocalName())
                    || !"http://schemas.xmlsoap.org/soap/envelope/".equals(envelope.getNamespaceURI())) {
                throw new Fault(402, "Invalid Args");
            }
            Element soapBody = child(envelope, "http://schemas.xmlsoap.org/soap/envelope/", "Body");
            Element action = null;
            for (Node n = soapBody.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n instanceof Element) {
                    if (action != null) throw new Fault(402, "Invalid Args");
                    action = (Element) n;
                }
            }
            if (action == null || !service.equals(action.getNamespaceURI())) throw new Fault(401, "Invalid Action");
            String name = action.getLocalName();
            String header = soapAction == null ? "" : soapAction.trim().replace("\"", "");
            if (!header.equals(service + "#" + name)) throw new Fault(401, "Invalid Action");
            Map<String, String> args = new LinkedHashMap<String, String>();
            for (Node n = action.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (!(n instanceof Element)) continue;
                Element e = (Element) n;
                if (args.put(e.getLocalName(), e.getTextContent()) != null) throw new Fault(402, "Invalid Args");
            }
            String result = invoke(service, name, args);
            return new Reply(200, envelope("<u:" + name + "Response xmlns:u=\"" + service + "\">"
                    + result + "</u:" + name + "Response>"));
        } catch (Fault fault) {
            return new Reply(500, envelope("<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring>"
                    + "<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>"
                    + fault.code + "</errorCode><errorDescription>" + escape(fault.getMessage())
                    + "</errorDescription></UPnPError></detail></s:Fault>"));
        } catch (Exception error) {
            return new Reply(500, envelope("<s:Fault><faultcode>s:Client</faultcode><faultstring>UPnPError</faultstring>"
                    + "<detail><UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\"><errorCode>501</errorCode>"
                    + "<errorDescription>Action Failed</errorDescription></UPnPError></detail></s:Fault>"));
        }
    }

    private String invoke(String service, String name, Map<String, String> a) throws Fault {
        PlaybackState.Snapshot s = state.snapshot();
        if (CMS.equals(service)) {
            if ("GetProtocolInfo".equals(name)) return tag("Source", "") + tag("Sink", SINK);
            if ("GetCurrentConnectionIDs".equals(name)) return tag("ConnectionIDs", "0");
            if ("GetCurrentConnectionInfo".equals(name)) {
                if (integer(a, "ConnectionID", 0, Integer.MAX_VALUE) != 0) throw new Fault(706, "Invalid Connection Reference");
                return tag("RcsID", "0") + tag("AVTransportID", "0") + tag("ProtocolInfo", SINK.split(",")[0])
                        + tag("PeerConnectionManager", "") + tag("PeerConnectionID", "-1")
                        + tag("Direction", "Input") + tag("Status", "OK");
            }
            throw new Fault(401, "Invalid Action");
        }
        if (integer(a, "InstanceID", 0, Integer.MAX_VALUE) != 0) throw new Fault(718, "Invalid InstanceID");
        if (RCS.equals(service)) {
            if ("ListPresets".equals(name)) return tag("CurrentPresetNameList", "FactoryDefaults");
            if (!"Master".equals(required(a, "Channel"))) throw new Fault(402, "Invalid Args");
            if ("GetVolume".equals(name)) return tag("CurrentVolume", String.valueOf(s.volume));
            if ("GetMute".equals(name)) return tag("CurrentMute", s.muted ? "1" : "0");
            if ("SetVolume".equals(name)) { controller.execute(new Command(name, integer(a, "DesiredVolume", 0, 100))); return ""; }
            if ("SetMute".equals(name)) {
                String mute = required(a, "DesiredMute");
                if (!mute.matches("0|1|true|false")) throw new Fault(402, "Invalid Args");
                controller.execute(new Command(name, "1".equals(mute) || "true".equals(mute) ? 1 : 0)); return "";
            }
            throw new Fault(401, "Invalid Action");
        }
        if ("SetAVTransportURI".equals(name)) {
            String uri = mediaUrl(required(a, "CurrentURI"));
            String metadata = required(a, "CurrentURIMetaData");
            String title = "手机投屏", subtitle = null;
            if (metadata.length() > 32768) throw new Fault(402, "Metadata Too Large");
            if (!metadata.trim().isEmpty()) {
                Document didl = xml(metadata);
                if (!"DIDL-Lite".equals(didl.getDocumentElement().getLocalName())
                        || !"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/".equals(didl.getDocumentElement().getNamespaceURI())) {
                    throw new Fault(402, "Invalid Metadata");
                }
                NodeList titles = didl.getElementsByTagNameNS("http://purl.org/dc/elements/1.1/", "title");
                if (titles.getLength() > 0) title = titles.item(0).getTextContent();
                NodeList nodes = didl.getElementsByTagName("*");
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element e = (Element) nodes.item(i);
                    String local = e.getLocalName().toLowerCase(Locale.US);
                    if (local.contains("caption") || local.contains("subtitle")) {
                        String value = e.getTextContent().trim();
                        if (!value.isEmpty()) { subtitle = mediaUrl(value); break; }
                    }
                }
            }
            title = title.replaceAll("[\\p{Cntrl}\\s]+", " ").trim();
            if (title.length() > 120) title = title.substring(0, 120);
            if (subtitle == null) subtitle = subtitleFromVideoUrl(uri);
            controller.execute(new Command(name, uri, title, metadata, subtitle, 0));
            return "";
        }
        if ("GetTransportInfo".equals(name)) return tag("CurrentTransportState", s.transport)
                + tag("CurrentTransportStatus", s.status) + tag("CurrentSpeed", "1");
        if ("GetPositionInfo".equals(name)) return tag("Track", s.uri.isEmpty() ? "0" : "1")
                + tag("TrackDuration", time(s.duration)) + tag("TrackMetaData", s.metadata) + tag("TrackURI", s.uri)
                + tag("RelTime", time(s.position)) + tag("AbsTime", time(s.position))
                + tag("RelCount", "2147483647") + tag("AbsCount", "2147483647");
        if ("GetMediaInfo".equals(name)) return tag("NrTracks", s.uri.isEmpty() ? "0" : "1")
                + tag("MediaDuration", time(s.duration)) + tag("CurrentURI", s.uri) + tag("CurrentURIMetaData", s.metadata)
                + tag("NextURI", "") + tag("NextURIMetaData", "") + tag("PlayMedium", s.uri.isEmpty() ? "NONE" : "NETWORK")
                + tag("RecordMedium", "NOT_IMPLEMENTED") + tag("WriteStatus", "NOT_IMPLEMENTED");
        if ("GetDeviceCapabilities".equals(name)) return tag("PlayMedia", "NETWORK")
                + tag("RecMedia", "NOT_IMPLEMENTED") + tag("RecQualityModes", "NOT_IMPLEMENTED");
        if ("GetTransportSettings".equals(name)) return tag("PlayMode", "NORMAL") + tag("RecQualityMode", "NOT_IMPLEMENTED");
        if ("GetCurrentTransportActions".equals(name)) return tag("Actions", transportActions(s));
        if ("Play".equals(name)) {
            if (!"1".equals(required(a, "Speed"))) throw new Fault(717, "Play Speed Not Supported");
            requireMedia(s); controller.execute(new Command(name, 0)); return "";
        }
        if ("Pause".equals(name)) { requireMedia(s); controller.execute(new Command(name, 0)); return ""; }
        if ("Stop".equals(name)) { controller.execute(new Command(name, 0)); return ""; }
        if ("Seek".equals(name)) {
            requireMedia(s);
            if (!"REL_TIME".equals(required(a, "Unit"))) throw new Fault(710, "Seek Mode Not Supported");
            if (s.duration <= 0 || "TRANSITIONING".equals(s.transport)) throw new Fault(710, "Stream Not Seekable");
            int target = parseTime(required(a, "Target"));
            if (target > s.duration) throw new Fault(711, "Illegal Seek Target");
            controller.execute(new Command(name, target)); return "";
        }
        throw new Fault(401, "Invalid Action");
    }

    private static void requireMedia(PlaybackState.Snapshot s) throws Fault {
        if (s.uri.isEmpty() || !"OK".equals(s.status)) throw new Fault(701, "Transition Not Available");
    }
    static String transportActions(PlaybackState.Snapshot s) {
        if (s.uri.isEmpty()) return "";
        if (!"OK".equals(s.status)) return "Stop";
        return "Play,Pause,Stop" + (s.duration > 0 && !"TRANSITIONING".equals(s.transport) ? ",Seek" : "");
    }
    private static String required(Map<String, String> args, String name) throws Fault {
        if (!args.containsKey(name)) throw new Fault(402, "Invalid Args");
        return args.get(name);
    }
    private static int integer(Map<String, String> args, String key, int min, int max) throws Fault {
        try {
            int value = Integer.parseInt(required(args, key));
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) { throw new Fault(402, "Invalid Args"); }
    }
    static String mediaUrl(String value) throws Fault {
        try {
            if (value.length() > 8192 || value.matches("(?s).*[\\p{Cntrl}].*")) throw new Exception();
            URI uri = new URI(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535) throw new Exception();
            return value;
        } catch (Exception error) { throw new Fault(714, "Unsupported or Invalid URI"); }
    }
    static int parseTime(String value) throws Fault {
        try {
            if (!value.matches("\\d{1,3}:[0-5]\\d:[0-5]\\d(?:\\.\\d{1,3})?")) throw new Exception();
            String[] p = value.split(":");
            long ms = Long.parseLong(p[0]) * 3600000L + Long.parseLong(p[1]) * 60000L
                    + Math.round(Double.parseDouble(p[2]) * 1000);
            if (ms > Integer.MAX_VALUE) throw new Exception();
            return (int) ms;
        } catch (Exception error) { throw new Fault(711, "Illegal Seek Target"); }
    }
    static String subtitleFromVideoUrl(String value) {
        try {
            String query = new URI(value).getRawQuery();
            if (query == null) return null;
            for (String parameter : query.split("&")) {
                int equal = parameter.indexOf('='); if (equal < 0) continue;
                String name = URLDecoder.decode(parameter.substring(0, equal), "UTF-8").toLowerCase(Locale.US);
                if (name.matches("subtitle|subtitleurl|subtitle_url|caption|captionurl|caption_url|srt|vtt|sub")) {
                    return mediaUrl(URLDecoder.decode(parameter.substring(equal + 1), "UTF-8"));
                }
            }
        } catch (Exception invalid) { }
        return null;
    }
    static String time(int ms) {
        int seconds = Math.max(0, ms) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
    static String tag(String name, String value) { return "<" + name + ">" + escape(value) + "</" + name + ">"; }
    private static String envelope(String body) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>" + body + "</s:Body></s:Envelope>";
    }
    static Document xml(String source) throws Fault {
        try {
            String upper = source.toUpperCase(Locale.US);
            if (source.length() > 65536 || upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) throw new Exception();
            // Bound nesting before building a DOM (not just after allocation).
            SAXParserFactory sax = SAXParserFactory.newInstance();
            sax.setNamespaceAware(true);
            sax.newSAXParser().parse(new InputSource(new StringReader(source)), new DefaultHandler() {
                private int depth, count;
                @Override public void startElement(String ns, String local, String q, org.xml.sax.Attributes a) throws SAXException {
                    if (++depth > 24 || ++count > 1024) throw new SAXException("XML structure limit");
                }
                @Override public void endElement(String ns, String local, String q) { depth--; }
                @Override public InputSource resolveEntity(String publicId, String systemId) throws SAXException { throw new SAXException("External entities disabled"); }
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            // Android's parser does not support all Xerces features; the DTD rejection above is mandatory.
            String[] features = {"http://xml.org/sax/features/external-general-entities",
                    "http://xml.org/sax/features/external-parameter-entities",
                    "http://apache.org/xml/features/nonvalidating/load-external-dtd"};
            for (String feature : features) {
                try { factory.setFeature(feature, false); } catch (Exception unsupported) { }
            }
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            Document result = builder.parse(new InputSource(new StringReader(source)));
            checkTree(result.getDocumentElement(), 0, new int[]{0});
            return result;
        } catch (Exception error) { throw new Fault(402, "Invalid XML"); }
    }
    private static void checkTree(Node node, int depth, int[] count) throws Exception {
        if (depth > 24 || ++count[0] > 2048) throw new Exception();
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) checkTree(n, depth + 1, count);
    }
    private static Element child(Element parent, String namespace, String name) throws Fault {
        Element result = null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element && name.equals(n.getLocalName()) && namespace.equals(n.getNamespaceURI())) {
                if (result != null) throw new Fault(402, "Invalid Args");
                result = (Element) n;
            }
        }
        if (result == null) throw new Fault(402, "Invalid Args");
        return result;
    }
}
