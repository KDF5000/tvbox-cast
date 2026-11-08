package local.tvbox.castlauncher;

import java.util.LinkedHashMap;
import java.util.Map;

/** Device and service descriptions advertise only the actions implemented by DlnaProtocol. */
final class DlnaDescription {
    static String device(String uuid, String name) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root xmlns=\"urn:schemas-upnp-org:device-1-0\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion><device>"
                + DlnaProtocol.tag("deviceType", DlnaProtocol.DEVICE)
                + DlnaProtocol.tag("friendlyName", name) + "<manufacturer>Local TVBox</manufacturer>"
                + "<modelName>大屏投屏</modelName><modelNumber>2.0</modelNumber>"
                + DlnaProtocol.tag("UDN", uuid) + "<serviceList>"
                + service("AVTransport", DlnaProtocol.AVT, "avt")
                + service("RenderingControl", DlnaProtocol.RCS, "rcs")
                + service("ConnectionManager", DlnaProtocol.CMS, "cms")
                + "</serviceList><presentationURL>/</presentationURL></device></root>";
    }
    private static String service(String name, String type, String path) {
        return "<service>" + DlnaProtocol.tag("serviceType", type)
                + DlnaProtocol.tag("serviceId", "urn:upnp-org:serviceId:" + name)
                + DlnaProtocol.tag("SCPDURL", "/" + path + ".xml")
                + DlnaProtocol.tag("controlURL", "/control/" + path)
                + DlnaProtocol.tag("eventSubURL", "/event/" + path) + "</service>";
    }
    static String scpd(String service) {
        Spec s = new Spec();
        if (DlnaProtocol.AVT.equals(service)) {
            s.variable("A_ARG_TYPE_InstanceID", "ui4");
            s.variable("A_ARG_TYPE_SeekMode", "string", "REL_TIME");
            s.variable("TransportState", "string", "STOPPED", "PLAYING", "TRANSITIONING", "PAUSED_PLAYBACK", "NO_MEDIA_PRESENT");
            s.variable("TransportStatus", "string", "OK", "ERROR_OCCURRED");
            s.variable("TransportPlaySpeed", "string", "1");
            s.action("SetAVTransportURI", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:CurrentURI:AVTransportURI", "i:CurrentURIMetaData:AVTransportURIMetaData");
            s.action("Play", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:Speed:TransportPlaySpeed");
            s.action("Pause", "i:InstanceID:A_ARG_TYPE_InstanceID");
            s.action("Stop", "i:InstanceID:A_ARG_TYPE_InstanceID");
            s.action("Seek", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:Unit:A_ARG_TYPE_SeekMode", "i:Target:A_ARG_TYPE_SeekTarget");
            s.action("GetTransportInfo", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:CurrentTransportState:TransportState", "o:CurrentTransportStatus:TransportStatus", "o:CurrentSpeed:TransportPlaySpeed");
            s.variable("CurrentTrack", "ui4");
            s.action("GetPositionInfo", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:Track:CurrentTrack", "o:TrackDuration:CurrentTrackDuration", "o:TrackMetaData:CurrentTrackMetaData", "o:TrackURI:CurrentTrackURI", "o:RelTime:RelativeTimePosition", "o:AbsTime:AbsoluteTimePosition", "o:RelCount:RelativeCounterPosition", "o:AbsCount:AbsoluteCounterPosition");
            s.variable("NumberOfTracks", "ui4");
            s.variable("RelativeCounterPosition", "i4");
            s.variable("AbsoluteCounterPosition", "i4");
            s.action("GetMediaInfo", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:NrTracks:NumberOfTracks", "o:MediaDuration:CurrentMediaDuration", "o:CurrentURI:AVTransportURI", "o:CurrentURIMetaData:AVTransportURIMetaData", "o:NextURI:NextAVTransportURI", "o:NextURIMetaData:NextAVTransportURIMetaData", "o:PlayMedium:PlaybackStorageMedium", "o:RecordMedium:RecordStorageMedium", "o:WriteStatus:RecordMediumWriteStatus");
            s.action("GetDeviceCapabilities", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:PlayMedia:PossiblePlaybackStorageMedia", "o:RecMedia:PossibleRecordStorageMedia", "o:RecQualityModes:PossibleRecordQualityModes");
            s.action("GetTransportSettings", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:PlayMode:CurrentPlayMode", "o:RecQualityMode:CurrentRecordQualityMode");
            s.action("GetCurrentTransportActions", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:Actions:CurrentTransportActions");
            s.event("LastChange");
        } else if (DlnaProtocol.RCS.equals(service)) {
            s.variable("A_ARG_TYPE_InstanceID", "ui4");
            s.variable("A_ARG_TYPE_Channel", "string", "Master");
            s.variable("Volume", "ui2");
            s.variable("Mute", "boolean");
            s.action("ListPresets", "i:InstanceID:A_ARG_TYPE_InstanceID", "o:CurrentPresetNameList:PresetNameList");
            s.action("GetVolume", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:Channel:A_ARG_TYPE_Channel", "o:CurrentVolume:Volume");
            s.action("SetVolume", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:Channel:A_ARG_TYPE_Channel", "i:DesiredVolume:Volume");
            s.action("GetMute", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:Channel:A_ARG_TYPE_Channel", "o:CurrentMute:Mute");
            s.action("SetMute", "i:InstanceID:A_ARG_TYPE_InstanceID", "i:Channel:A_ARG_TYPE_Channel", "i:DesiredMute:Mute");
            s.event("LastChange");
        } else {
            s.variable("A_ARG_TYPE_ConnectionID", "i4");
            s.variable("A_ARG_TYPE_RcsID", "i4");
            s.variable("A_ARG_TYPE_AVTransportID", "i4");
            s.action("GetProtocolInfo", "o:Source:SourceProtocolInfo", "o:Sink:SinkProtocolInfo");
            s.action("GetCurrentConnectionIDs", "o:ConnectionIDs:CurrentConnectionIDs");
            s.action("GetCurrentConnectionInfo", "i:ConnectionID:A_ARG_TYPE_ConnectionID", "o:RcsID:A_ARG_TYPE_RcsID", "o:AVTransportID:A_ARG_TYPE_AVTransportID", "o:ProtocolInfo:A_ARG_TYPE_ProtocolInfo", "o:PeerConnectionManager:A_ARG_TYPE_ConnectionManager", "o:PeerConnectionID:A_ARG_TYPE_ConnectionID", "o:Direction:A_ARG_TYPE_Direction", "o:Status:A_ARG_TYPE_ConnectionStatus");
            s.event("SourceProtocolInfo"); s.event("SinkProtocolInfo"); s.event("CurrentConnectionIDs");
        }
        return "<?xml version=\"1.0\"?><scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">"
                + "<specVersion><major>1</major><minor>0</minor></specVersion><actionList>"
                + s.actions + "</actionList><serviceStateTable>" + s.variables() + "</serviceStateTable></scpd>";
    }
    private static final class Spec {
        final StringBuilder actions = new StringBuilder();
        final Map<String, String> vars = new LinkedHashMap<String, String>();
        final Map<String, Boolean> events = new LinkedHashMap<String, Boolean>();
        void variable(String name, String type, String... allowed) {
            String value = DlnaProtocol.tag("name", name) + DlnaProtocol.tag("dataType", type);
            if (allowed.length > 0) {
                value += "<allowedValueList>";
                for (String a : allowed) value += DlnaProtocol.tag("allowedValue", a);
                value += "</allowedValueList>";
            }
            if ("Volume".equals(name)) value += "<allowedValueRange><minimum>0</minimum><maximum>100</maximum><step>1</step></allowedValueRange>";
            vars.put(name, value);
        }
        void event(String name) { if (!vars.containsKey(name)) variable(name, "string"); events.put(name, true); }
        void action(String name, String... args) {
            actions.append("<action>").append(DlnaProtocol.tag("name", name));
            if (args.length > 0) actions.append("<argumentList>");
            for (String arg : args) {
                String[] p = arg.split(":");
                if (!vars.containsKey(p[2])) variable(p[2], "string");
                actions.append("<argument>").append(DlnaProtocol.tag("name", p[1]))
                        .append(DlnaProtocol.tag("direction", "i".equals(p[0]) ? "in" : "out"))
                        .append(DlnaProtocol.tag("relatedStateVariable", p[2])).append("</argument>");
            }
            if (args.length > 0) actions.append("</argumentList>");
            actions.append("</action>");
        }
        String variables() {
            StringBuilder result = new StringBuilder();
            for (Map.Entry<String, String> v : vars.entrySet()) result.append("<stateVariable sendEvents=\"")
                    .append(events.containsKey(v.getKey()) ? "yes" : "no").append("\">").append(v.getValue()).append("</stateVariable>");
            return result.toString();
        }
    }
}
