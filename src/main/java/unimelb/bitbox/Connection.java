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
import java.util.ArrayList;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

public class Connection extends Thread {
    BufferedReader in;
    BufferedWriter out;
    protected Socket socket;
    private MessageHandler handler;
    
    private HostPort clientHostPort;

    public Connection(Socket socket, MessageHandler handler) {
        this.socket = socket;
        this.in = null;
        this.out = null;
        this.handler = handler;
    }

    public void run() {
        try {
			out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
			in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
			//The first protocol received from a new potential client should be a handshake request
			Document handshake = Document.parse(in.readLine());
			System.out.println(handshake.toJson());
			HostPort hp = null;
			if(handshake.getString("command").equals("HANDSHAKE_REQUEST")) {
				try {
					//Will throw a ClassCastException here if the port field is not a integer
					clientHostPort = new HostPort((Document) handshake.get("hostPort"));
					if(clientHostPort.host != null) {
						if(Server.clientCount.get() < Server.maximumConnections) {
							Server.clientCount.getAndIncrement();
							out.write(Protocol.createHandshakeResponseP(Server.localHostPort));
							out.flush();
							handler.fileSystemManager.generateSyncEvents();
							Server.connections.put(this, clientHostPort);
							System.out.println("Now we have:"+Server.clientCount.get());
						}else {
							out.write(Protocol.createConnectionRefusedP(new ArrayList<HostPort>(Server.connections.values())));
							out.flush();
							socket.close();
							return;
						}
					}else {
						out.write(Protocol.createInvalidP("The host name should not be null!"));
						out.flush();
						socket.close();
						return;
					}
				} catch (NullPointerException npe) {
					out.write(Protocol.createInvalidP("Your handshake request should contain a hostPort field with not-null port and host!"));
					out.flush();
					socket.close();
					return;
				} catch (ClassCastException cce) {
					out.write(Protocol.createInvalidP("The port number should be an integer!"));
					out.flush();
					socket.close();
					return;
				}
			}else {
				out.write(Protocol.createInvalidP("Your first message should be a handshake request rather than any other msg!"));
				out.flush();
				socket.close();
				return;
			}

	        while (true) {
	            String msg = in.readLine();
	            if(msg == null) {
					System.out.println("Connection closed remotely ");
					socket.close();
					Server.clientCount.decrementAndGet();
					Server.connections.remove(this);
					System.out.println("Now we only have: "+Server.clientCount.get());
					return;
				}
	            ArrayList<Document> responses = handler.handleMsg(msg);
				if (responses != null) {
					for (Document r : responses) {
						out.write(r.toJson() + System.lineSeparator());
						out.flush();
					}
				}
	        }
	        
		} catch (EOFException e) {
			System.out.println("Connection closed");
		} catch (IOException e) {
			e.printStackTrace();
		}

    }

}