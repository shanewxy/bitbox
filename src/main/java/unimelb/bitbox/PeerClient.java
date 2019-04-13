package unimelb.bitbox;

import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import unimelb.bitbox.util.Protocol;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;

public class PeerClient {
	
	private BufferedWriter out;
	private BufferedReader in;
	
	private Socket client;
	
	private HostPort targetHostPort;

	private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));
	
//	/**
//	 * Take a string as argument and judge the type of command of this string
//	 * @param commandStr Received command in the form of String
//	 * @return An integer indicates type of the command
//	 */
//	private int parseCommand(Document command) {
//		int result = 0;
//		
//		if(command.toJson().equals("{}")){
//			result = -1;
//		}
//		
//		return result;
//	}
	
	/**
	 * Main part of processing incoming commands since a connection to a certain peer is successfully established
	 */
	public void processMsg() {
		while(true) {
			try {
				System.out.println(in.readLine());;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Try to connect with other available peers when a peer refused our connection
	 * @param peers A list of other available peers in the form of Document which provided by the remote peer
	 */
	private void connectOtherPeers(ArrayList<Document> peers) {
		
	}
	
	public PeerClient(String ip, int port) {
		targetHostPort = new HostPort(ip, port);
	}
	
	/**
	 * This validating method is only used for parsing incoming command during initial handshake phase
	 * @param cmd the received command, in the form of Document
	 * @return -1: the received command is invalid, send invalid protocol back and disconnect;
	 * 		   	0: the remote peer tell us it is full, try to connect with other peers that listed in the received command;
	 * 			1: the handshake request is granted.
	 */
	private int validateInitialProtocol(Document proto) {
		
		//This try-catch is too general because we don't know what happens when try to call get method to an empty JSON object
		try {
			//The name of command written in the validating protocol
			String commandField = proto.getString("command");
			
			if(commandField != null) {
				if(commandField.equals("HANDSHAKE_RESPONSE")) {
					//An exception would be catch if something goes wrong while parsing the hostPort field of the protocol
					HostPort hpField = new HostPort((Document) proto.get("hostPort"));
					return 1;
				}else if(commandField.equals("CONNECTION_REFUSED")) {
					/*Don't care about other fields in the protocol because the connection will soon ended
					 * if the received peer list is invalid, just regard it as an invalid protocol, but the 
					 * connection is ended so it doesn't matter.
					 */
					return 0;
				}else {
					return -1;
				}
			}else {
				return -1;
			}
		} catch(Exception e) {
			return -1;
		}
	}
	
	@SuppressWarnings("unchecked")
	public Boolean initConnection() {
		
		try {
			client = new Socket(targetHostPort.host, targetHostPort.port);
			System.out.println("Initial connection established with peer: " + targetHostPort.toString());
			
			System.out.println("Sending handshake request...");
			out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), "UTF-8"));
			in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
			String h = Protocol.createHandshakeRequestP(localHostPort);
			System.out.println(h);
			out.write(h);
			out.flush();
			
			System.out.println("Waiting for handshake response...");
			String s = in.readLine();
			System.out.println(s);
			Document receivedCommand = Document.parse(s);
			switch(validateInitialProtocol(receivedCommand)) {
				case -1:
					System.out.println("Received invalid protocol");
					out.write(Protocol.createInvalidP("Invalid Message"));
					out.flush();
					in.close();
					out.close();
					client.close();
					return false;
				case 0:
					System.out.println("The remote peer is already full, try other peers that provided by the remote one");
					connectOtherPeers((ArrayList<Document>) receivedCommand.get("peers"));
					return false;
				case 1:
					System.out.println("Handshake request granted!");
//					processMsg();
					return true;
			}
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.out.println("Connection failed with remote peer: " + targetHostPort.toString());
			return false;
		}
		
		return false;
	}
}
