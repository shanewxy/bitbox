package unimelb.bitbox;

import java.net.Socket;
import java.net.UnknownHostException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import unimelb.bitbox.util.Protocol;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

public class PeerClient {
	
	private DataOutputStream out;
	private DataInputStream in;

	private static HostPort myHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));
	
	/**
	 * Take a string as argument and judge the type of command of this string
	 * @param commandStr Received command in the form of String
	 * @return An integer indicates type of the command
	 */
	private int parseCommand(String commandStr) {
		int result = 0;
		
		
		return result;
	}
	
	/**
	 * Main part of processing incoming commands since a connection to a certain peer is successfully established
	 */
	private void processMsg() {
		
	}
	
	/**
	 * Try to connect with other available peers when a peer refused our connection
	 * @param peers A list of other available peers in the form of Document which provided by the remote peer
	 */
	private void connectOtherPeers(Document peers) {
		
	}
	
	public PeerClient(String ip, int port) {
		try {
			Socket s = new Socket(ip, port);
			System.out.println("Initial connection established with port: " + port + "//"+System.currentTimeMillis());
			out = new DataOutputStream(s.getOutputStream());
			System.out.println("Sending handshake request...");
			out.writeUTF(Protocol.createHandshakeRequestP(myHostPort).toJson());
			out.flush();
			
			in = new DataInputStream(s.getInputStream());
			String responseStr = in.readUTF();
			int feedback = parseCommand(responseStr);
			switch(feedback) {
				case 1:
					System.out.println("Success!!!");
					processMsg();
					break;
				case 2:
					System.out.println("Connection refused by peer: "+ ip + ":" + port + ", try to connect other peers...");
					JSONObject commandObj = (JSONObject) new JSONParser().parse(responseStr);
					connectOtherPeers(new Document(commandObj));
					break;
				case -1:
					System.out.println("Recieved a invalid protocol, send feedback and disconnect...");
					out.writeUTF(Protocol.createInvalidP("Invalid Message").toJson());
					out.flush();
					in.close();
					out.close();
					s.close();
					break;
//				case ?:
//					Received an improper protocol
			}
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Connection failed with server => " + ip +":"+port);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}
