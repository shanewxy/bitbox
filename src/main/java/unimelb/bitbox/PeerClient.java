package unimelb.bitbox;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class PeerClient {

//	private String ipAddr;
//	private int portNum;
	
	public PeerClient(String ip, int port) {
		try {
			Socket s = new Socket(ip, port);
			System.out.println("Connection established with port: " + port + "//"+System.currentTimeMillis());
			
			DataInputStream input = new DataInputStream(s.getInputStream());
			DataOutputStream output = new DataOutputStream(s.getOutputStream());
			
			//Greeting
			output.writeUTF("Hello there server " + port + ":)~" + "//"+System.currentTimeMillis());
			output.flush();
			System.out.println("Recieved: " + input.readUTF() + "//"+System.currentTimeMillis());
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
