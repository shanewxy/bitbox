package unimelb.bitbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import unimelb.bitbox.util.Protocol;

public class Client implements Runnable{

	private DataInputStream in;
	private DataOutputStream out;
	private Socket socket;
	private static Logger log = Logger.getLogger(Client.class.getName());
	
	
	public void connectToPeerServer(String peer) {
		String address = peer.split(":")[0];
		int port = Integer.parseInt(peer.split(":")[1]);
		try {
			socket = new Socket(address, port);
			in = new DataInputStream(socket.getInputStream());
			out = new DataOutputStream(socket.getOutputStream());
			
			System.out.println("connected to server " + address + " : "+port);
//			sendToServer(socket.getLocalPort()+" says hello");
			new Thread(this).start();
			
			//sendToServer(Protocol.handshakeRequest(address, port).toString());
			
		} catch (UnknownHostException e) {
			System.out.println(e.getMessage());
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}

	public void sendToServer(String string) {
		try {
			if (out!=null) {
				out.writeUTF(string);
			}
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
	}
	
	@Override
	public void run() {
		while (true) {
			try {
				String msg  = in.readUTF();
				System.out.println(msg);
				// TODO handle msg received from server
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}
	
}
