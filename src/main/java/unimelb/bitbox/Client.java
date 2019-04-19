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

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

public class Client implements Runnable {
    BufferedReader in;
    BufferedWriter out;
    public boolean connected = false;
    private MessageHandler handler;
    
    private HostPort targetHostPort;
    
    private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));

    public Client(String peer, MessageHandler handler) {
        this.handler = handler;
        targetHostPort = new HostPort(peer);
        try {
            Socket s = new Socket(targetHostPort.host, targetHostPort.port);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
            
            System.out.println("Initial socketd established: " + targetHostPort.toString());
            if(initConnection(s)) {
            	connected = true;
            	new Thread(this).start();
            }
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
            out.write(msg+"\n");
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
//                Thread.sleep(1000);
                data = in.readLine();
                if (data != null) {
                    handler.handleMsg(data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
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
    
	public Boolean initConnection(Socket client) {
		
		try {
//			client = new Socket(targetHostPort.host, targetHostPort.port);
//			System.out.println("Initial connection established with peer: " + targetHostPort.toString());
			
			System.out.println("Sending handshake request...");
			
			out.write(Protocol.createHandshakeRequestP(localHostPort));
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
//					connectOtherPeers((ArrayList<Document>) receivedCommand.get("peers"));
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
