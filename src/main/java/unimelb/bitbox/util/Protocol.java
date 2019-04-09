package unimelb.bitbox.util;

import java.util.ArrayList;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
/**
 * This class contains templates of all essential protocols that used in the entire peers network.
 * Each type of protocol is an object of the provided Document class, which has already been set up 
 * with some fixed attributes.
 * @author Kiwilyc
 *
 */
public class Protocol {
	
	public static Document createInvalidP(String wrongMsg) {
		Document doc = new Document();
		
		doc.append("command", "INVALID_PROTOCOL");
		doc.append("message", wrongMsg);
		return doc;
	}

	public static Document createConnectionRefusedP(ArrayList<HostPort> peerList) {
		Document doc = new Document();
		ArrayList<Document> peers = new ArrayList<Document>();
		
		for(HostPort hp : peerList) {
			peers.add(hp.toDoc());
		}
		
		doc.append("command", "CONNECTION_REFUSED");
		doc.append("message", "connection limit reached");
		doc.append("peers", peers);
		return doc;
	}
	
	public static Document createHandshakeRequestP(HostPort hp) {
		Document doc = new Document();
		
		doc.append("command", "HANDSHAKE_REQUEST");
		doc.append("hostPort", hp.toDoc());
		return doc;
	}
	
	public static Document createHandshakeResponseP(HostPort hp) {
		Document doc = new Document();
		
		doc.append("command", "HANDSHAKE_RESPONSE");
		doc.append("hostPort", hp.toDoc());
		return doc;
	}
}
