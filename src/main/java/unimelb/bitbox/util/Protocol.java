package unimelb.bitbox.util;

import java.util.ArrayList;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
/**
 * This class contains templates of all essential protocols that used in the entire peers network.
 * Each type of protocol is a string in the format of JSON.
 * @author Kiwilyc
 *
 */
public class Protocol {
	
//	private final static String FILE_BITES_REQUEST = "FILE_BITES_REQUEST";
//	private final static String FILE_BITES_RESPONSE = "FILE_BITES_RESPONSE";
//	private final static String FILE_CREATE_REQUEST = "FILE_CREATE_REQUEST";
//	private final static String FILE_CREATE_RESPONSE = "FILE_CREATE_RESPONSE";
	
	public static String createInvalidP(String wrongMsg) {
		Document doc = new Document();
		
		doc.append("command", "INVALID_PROTOCOL");
		doc.append("message", wrongMsg);
		return doc.toJson()+System.lineSeparator();
	}

	public static String createConnectionRefusedP(ArrayList<HostPort> peerList) {
		Document doc = new Document();
		ArrayList<Document> peers = new ArrayList<Document>();
		
		for(HostPort hp : peerList) {
			peers.add(hp.toDoc());
		}
		
		doc.append("command", "CONNECTION_REFUSED");
		doc.append("message", "connection limit reached");
		doc.append("peers", peers);
		return doc.toJson()+System.lineSeparator();
	}
	
	public static String createHandshakeRequestP(HostPort hp) {
		Document doc = new Document();
		
		doc.append("command", "HANDSHAKE_REQUEST");
		doc.append("hostPort", hp.toDoc());
		return doc.toJson()+System.lineSeparator();
	}
	
	public static String createHandshakeResponseP(HostPort hp) {
		Document doc = new Document();
		
		doc.append("command", "HANDSHAKE_RESPONSE");
		doc.append("hostPort", hp.toDoc());
		return doc.toJson()+System.lineSeparator();
	}

//	public static String createFileCreateResponseP(){
//		
//	}
//	
	

}
