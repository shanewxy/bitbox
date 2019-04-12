package unimelb.bitbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;

public class Connection extends Thread {
	DataInputStream in;
	DataOutputStream out;
	Socket clientSocket;

	public Connection(Socket aclientSocket) {
		try {
			this.clientSocket = aclientSocket;
			this.in = new DataInputStream(clientSocket.getInputStream());
			this.out = new DataOutputStream(clientSocket.getOutputStream());
			//this.start();
		} catch (IOException e) {
			System.out.println("Connection: " + e.getMessage());
		}
	}

	@Override
	public void run() {
//		
//		try {
//			String data = in.readUTF();
//			out.writeUTF(data + " have received!");
//		} catch (EOFException e) {
//			System.out.println("EOF " + e.getMessage());
//		} catch (IOException e) {
//			System.out.println("Readline: " + e.getMessage());
//		} finally {
//			try {
//				clientSocket.close();
//			} catch (IOException e) {
//				System.out.println("close failed");
//			}
//		}
		
		while (true) {
			try {
				String data = in.readUTF();
				// TODO handle msg from client
				
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}
}
