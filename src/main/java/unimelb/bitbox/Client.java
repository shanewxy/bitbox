package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

public class Client implements Runnable {
	BufferedReader in;
	BufferedWriter out;
	public boolean connected = false;
	private MessageHandler handler;

	public Client(String peer, MessageHandler handler) {
		this.handler = handler;
		Socket s = null;
		String address = peer.split(":")[0];
		int port = Integer.parseInt(peer.split(":")[1]);
		try {
			s = new Socket(address, port);
			in = new BufferedReader(new InputStreamReader(s.getInputStream()));

			out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
			System.out.println("connected to server " + address+" : "+port);
			connected = true;
			// test connection
			new Thread(this).start();

			// send HANDSHAKE_REQUEST
			String doc = Protocol.createHandshakeRequestP(new HostPort(address, port));
			sendToServer(doc);
			// Document doc = new Document();
			// doc.append("command", "HANDSHAKE_REQUEST");
			// Document h = new Document();
			// h.append("host", "10.13.61.255");
			// h.append("port", 811);
			// doc.append("hostPort", h);
			 System.out.println(doc);

		} catch (UnknownHostException e) {
			System.out.println("Sock:" + e.getMessage());
		} catch (EOFException e) {
			System.out.println("EOF:" + e.getMessage());
		} catch (IOException e) {
			System.out.println("IO:" + e.getMessage());
		}
	}

	/**
	 * send message to server.
	 */
	public void sendToServer(String msg) {
		try {
			out.write(msg + "\n");
			out.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		while (true) {
			String data = null;
			try {
				Thread.sleep(1000);
				data = in.readLine();
				System.out.println("has received");
				if (data != null) {
					
					//handler.handleMsg(data);
					ArrayList<Document> responses = handler.handleMsg(data);
                	if (responses!=null) {
                		for (Document r : responses) {
                			sendToServer(r.toJson());
                		}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

}
