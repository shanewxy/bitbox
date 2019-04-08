package unimelb.bitbox;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PeerServer {
	private ServerSocket listenSocket = null;
	
	private void processConnection (Socket client) {
		try {
			DataInputStream input = new DataInputStream(client.getInputStream());
			DataOutputStream output = new DataOutputStream(client.getOutputStream());
			
			String test = input.readUTF();
			System.out.println("Server recieved: "+test + "//"+System.currentTimeMillis());
			
			output.writeUTF("Hello from server" + "//"+System.currentTimeMillis());
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
			
			while(true) {
				Socket client = listenSocket.accept();
				System.out.println("New client (" + client.getPort() + ") connected!" + "//"+System.currentTimeMillis());
				
				//Start a new thread to deal with the established connection.
				new Thread(() -> processConnection(client)).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
}
