package unimelb.bitbox;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.EOFException;
import java.io.IOException;

public class PeerServer {
	/* A list stores every connected peer's ipAddress and portNumber
	 * can be sent to other peers when refuse their connection requests
	 */
	private HashMap<Socket, HostPort> clients;
	
	private ServerSocket listenSocket;
	
	private FileSystemManager fsm;
	
	private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));
	private static int maximumConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	private void processMessages (Socket client) {
		try {
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), "UTF-8"));
			BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), "UTF-8"));
			//The first protocol received from a new potential client should be a handshake request
			Document handshake = Document.parse(in.readLine());
			System.out.println(handshake.toJson());
			HostPort hp = null;
			if(handshake.getString("command").equals("HANDSHAKE_REQUEST")) {
				try {
					//Will throw a ClassCastException here if the port field is not a integer
					hp = new HostPort((Document) handshake.get("hostPort"));
					if(hp.host != null) {
						synchronized (clients){
							if(clients.size() < maximumConnections) {
								clients.put(client, hp);
								out.write(Protocol.createHandshakeResponseP(localHostPort));
								out.flush();
							}else {
								out.write(Protocol.createConnectionRefusedP(new ArrayList<HostPort>(clients.values())));
								client.close();
								return;
							}
						}
					}else {
						out.write(Protocol.createInvalidP("The host name should not be null!"));
						client.close();
						return;
					}
				} catch (NullPointerException npe) {
					out.write(Protocol.createInvalidP("Your handshake request should contain a hostPort field with not-null port and host!"));
					client.close();
					return;
				} catch (ClassCastException cce) {
					out.write(Protocol.createInvalidP("The port number should be an integer!"));
					client.close();
					return;
				}
			}else {
				out.write(Protocol.createInvalidP("Your first message should be a handshake request rather than any other msg!"));
				client.close();
				return;
			}
			
			//Main processing part after handshake granted
			while(true) {
				String msg = in.readLine();
				if(msg == null) {
					System.out.println("Connection closed: " + clients.get(client));
					client.close();
					return;
				}
				System.out.println(msg);
			}
		} catch (EOFException e) {
			System.out.println("Connection closed: " + clients.get(client));
			clients.remove(client);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public PeerServer(int portNum, FileSystemManager fsm) {
		
		try {
			listenSocket = new ServerSocket(portNum);
			System.out.println("Server established on port: " + portNum + ", waiting..." + "//"+System.currentTimeMillis());
			//Initialize client peer list
			clients = new HashMap<Socket, HostPort>();
			this.fsm = fsm;
			while(true) {
				Socket client = listenSocket.accept();
				System.out.println("hello client: "+client.getInetAddress()+":"+client.getPort());
				//Start a new thread to deal with the established connection.
				new Thread(() -> processMessages(client)).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
