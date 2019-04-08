package unimelb.bitbox.util;

import java.util.ArrayList;

import unimelb.bitbox.util.Document;
/**
 * This class contains templates of all essential protocols that used in the entire peers network.
 * Each type of protocol is an object of the provided Document class, which has already been set up 
 * with some fixed attributes.
 * @author Kiwilyc
 *
 */
public class Protocol {
	
	public static Document createInvalidP() {
		Document doc = new Document();
		
		doc.append("command", "INVALID_PROTOCOL");
		doc.append("message", "message must contain a command filed as string");
		return doc;
	}

	public static Document createConnectionRefusedP(ArrayList<String> peerList) {
		Document doc = new Document();
		ArrayList<Document> peers = new ArrayList<Document>();
		
		for(String str : peerList) {
			Document peer = new Document();
			String[] peerInfo = str.split(":");
			peer.append("host", peerInfo[0]);
			peer.append("port", Integer.parseInt(peerInfo[1]));
			peers.add(peer);
		}
		
		doc.append("command", "CONNECTION_REFUSED");
		doc.append("message", "connection limit reached");
		doc.append("peers", peers);
		return doc;
	}
	
	public static Document createHandshakeRequestP(String host, int port) {
		Document doc = new Document();
		Document peer = new Document();
		
		peer.append("host", host);
		peer.append("port", port);
		doc.append("command", "HANDSHAKE_REQUEST");
		doc.append("hostPort", peer);
		return doc;
	}
	
	public static Document createHandshakeResponseP(String host, int port) {
		Document doc = new Document();
		Document peer = new Document();
		
		peer.append("host", host);
		peer.append("port", port);
		doc.append("command", "HANDSHAKE_RESPONSE");
		doc.append("hostPort", peer);
		return doc;
	}
}
