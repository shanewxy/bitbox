package unimelb.bitbox.util;

import java.util.ArrayList;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

/**
 * This class contains generating methods for protocols used in handshake phase
 * 
 * @author Kiwilyc
 */
public class Protocol {

    public static String createInvalidP(String wrongMsg) {
        Document doc = new Document();

        doc.append("command", "INVALID_PROTOCOL");
        doc.append("message", wrongMsg);
        return doc.toJson() + System.lineSeparator();
    }

    public static String createConnectionRefusedP(ArrayList<HostPort> peerList) {
        Document doc = new Document();
        ArrayList<Document> peers = new ArrayList<Document>();

        for (HostPort hp : peerList) {
            peers.add(hp.toDoc());
        }

        doc.append("command", "CONNECTION_REFUSED");
        doc.append("message", "connection limit reached");
        doc.append("peers", peers);
        return doc.toJson() + System.lineSeparator();
    }

    public static String createHandshakeRequestP(HostPort hp) {
        Document doc = new Document();

        doc.append("command", "HANDSHAKE_REQUEST");
        doc.append("hostPort", hp.toDoc());
        return doc.toJson() + System.lineSeparator();
    }

    public static String createHandshakeResponseP(HostPort hp) {
        Document doc = new Document();

        doc.append("command", "HANDSHAKE_RESPONSE");
        doc.append("hostPort", hp.toDoc());
        return doc.toJson() + System.lineSeparator();
    }

    public static String CreateAuthRequest(String identity) {
        Document doc = new Document();
        doc.append("command", "AUTH_REQUEST");
        doc.append("identity", identity);
        return doc.toJson() + System.lineSeparator();
    }
}
