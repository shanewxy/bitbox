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
					hp = new HostPort((Document) handshake.get("hostPort"));
					if(hp.host != null) {
						if(Server.clientCount.get() < Server.maximumConnections) {
							Server.clientCount.getAndIncrement();
							out.write(Protocol.createHandshakeResponseP(Server.localHostPort));
							out.flush();
						}else {
//							out.write(Protocol.createConnectionRefusedP(new ArrayList<HostPort>(clients.values())));
							socket.close();
							return;
						}
					}else {
						out.write(Protocol.createInvalidP("The host name should not be null!"));
						socket.close();
						return;
					}
				} catch (NullPointerException npe) {
					out.write(Protocol.createInvalidP("Your handshake request should contain a hostPort field with not-null port and host!"));
					socket.close();
					return;
				} catch (ClassCastException cce) {
					out.write(Protocol.createInvalidP("The port number should be an integer!"));
					socket.close();
					return;
				}
			}else {
				out.write(Protocol.createInvalidP("Your first message should be a handshake request rather than any other msg!"));
				socket.close();
				return;
			}

	        while (true) {
	            String msg = in.readLine();
	            if(msg == null) {
					System.out.println("Connection closed remotely ");
					socket.close();
					Server.clientCount.decrementAndGet();
					return;
				}
	            handler.handleMsg(msg);
	        }
	        
		} catch (EOFException e) {
			System.out.println("Connection closed");
			Server.clientCount.decrementAndGet();
		} catch (IOException e) {
			e.printStackTrace();
		}

    }

//    public boolean handleMsg(String msg) {
//        System.out.println(msg);
//        Document json = Document.parse(msg);
//        String cmd = null;
//        String pathName = null;
//        Long lastModified = null;
//        String md5 = null;
//        if (json.containsKey("command")) {
//            cmd = json.getString("command");
//        }
//        if (json.containsKey("pathName")) {
//            pathName = json.getString("pathName");
//        }
//        if (json.containsKey("fileDescriptor")) {
//            Document fileDescriptor = (Document) json.get("fileDescriptor");
//            if (fileDescriptor.containsKey("lastModified")) {
//                lastModified = fileDescriptor.getLong("lastModified");
//            }
//            if (json.containsKey("md5")) {
//                md5 = fileDescriptor.getString("md5");
//            }
//            if (json.containsKey("filezSize")) {
//                Long fileSize = fileDescriptor.getLong("fileSize");
//            }
//        }
//        boolean result = false;
//        switch (cmd) {
//        case "FILE_DELETE_REQUEST":
//            if (fileSystemManager.fileNameExists(pathName))
//                result = fileSystemManager.deleteFile(pathName, lastModified,
//                        md5);
//            break;
//        case "DIRECTORY_CREATE_REQUEST":
//            result = fileSystemManager.makeDirectory(pathName);
//            break;
//
//            
//        }
//            
//        return result;
//    }

}