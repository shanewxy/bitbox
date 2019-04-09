package unimelb.bitbox;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PeerServer {
	/* A list stores every connected peer's ipAddress and portNumber
	 * can be sent to other peers when refuse their connection requests
	 */
	private ArrayList<String> peerList;
	
	private ServerSocket listenSocket;
	
	private void processMessages (Socket client) {
		try {
			DataInputStream input = new DataInputStream(client.getInputStream());
			DataOutputStream output = new DataOutputStream(client.getOutputStream());
			
			String test = input.readUTF();
			System.out.println("Server recieved: "+test + "//"+System.currentTimeMillis());
			
			output.writeUTF("Hello from server");
			output.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public PeerServer(int portNum) {
		
		try {
			listenSocket = new ServerSocket(portNum);
			System.out.println("Server established on port: " + portNum + ", waiting..." + "//"+System.currentTimeMillis());
			//Initialize peer list
			peerList = new ArrayList<String>();
			
			while(true) {
				Socket client = listenSocket.accept();
				System.out.println("New client (" + client.getPort() + ") connected!" + "//"+System.currentTimeMillis());
				
				//Start a new thread to deal with the established connection.
				new Thread(() -> processMessages(client)).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
