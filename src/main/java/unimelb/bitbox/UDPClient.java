package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class UDPClient implements Runnable {

	private static Logger log = Logger.getLogger(UDPClient.class.getName());
	private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
	private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"),
			Integer.parseInt(Configuration.getConfigurationValue("udpPort")));
	private MessageHandler handler;
	private DatagramSocket ds;
	private DatagramPacket received;
	private DatagramPacket send;
	private byte[] data;
	private HostPort targetHostPort;
	private SocketAddress connectedHp;
	public boolean connected = false;
	public int attempts = 0;

	public UDPClient(String peer, MessageHandler handler) {
		this.handler = handler;
		this.targetHostPort = new HostPort(peer);
		try {
			this.ds = new DatagramSocket();
			this.data = new byte[12000];
			this.received = new DatagramPacket(data, data.length);
			ds.setSoTimeout(ServerMain.UDPTIMEOUT );

			makeConnection();
			
			if (connected) {
				
				log.info("successfully connected to " + targetHostPort.toString());
				new Thread(this).start();
			}
			

		} catch (SocketException e) {
			log.warning(e.getMessage());
		} catch (IOException e) {
			log.warning(e.getMessage());
		}
	}

	/**
	 * make connection to target host port, return true if received a hankshake
	 * response
	 */
	private void makeConnection() {
		do {
			// send handshake request to target host
			try {
				handShakeToPeer(targetHostPort);
				
				ds.receive(received);
				if (!received.getAddress().equals(send.getAddress())) {
					throw new IOException("Received packet from an unknown source");
				}

				if (received.getData().length > 0) {

					Document msg = Document.parse(new String(received.getData(),0,received.getLength(),"UTF-8"));
					String cmd = msg.getString("command");
					if (cmd != null) {

						if (cmd.equals("HANDSHAKE_RESPONSE")) {
							this.connected = true;
							this.connectedHp = received.getSocketAddress();
							new Thread(() -> broadcastSyncEvent()).start();
							break;
						} else if (cmd.equals("INVALID_PROTOCOL")) {
							log.info("received: INVALID_PROTOCOL from " + received.getAddress().getHostAddress() + " : "
									+ received.getPort());
						} else if (cmd.equals("CONNECTION_REFUSED")) {
							log.info(received.getAddress().getHostAddress() + " : " + received.getPort()
									+ "has reached maximum, trying to connect other peers...");
							connectToOtherPeers((ArrayList<Document>) msg.get("peers"));
						}
					}
				}

			} catch (IOException e) {
				attempts++;
				log.info("Timed out, " + (ServerMain.UDPATTEMPTS - attempts) + " more tries...");
			}

		} while (!connected && (attempts < ServerMain.UDPATTEMPTS));
		
		if (attempts >= ServerMain.UDPATTEMPTS) {
			log.info(targetHostPort.toString()+" refused connection");
		}

	}

	@Override
	public void run() {
		while (true) {
			try {
				data = new byte[12000];
				received = new DatagramPacket(data, data.length);
				ds.receive(received);

				// This should be included as the INVALID PROTOCAL, remote udp peer cannot inform the local peer that he has disconnected
				if (received.getData().length == 0) {
					this.connected = false;
					ds.close();
					return;
				}

				if (connected) {
					new Thread(new UDPThread(received, ds, handler)).start();
				}

			} catch (SocketTimeoutException e) {
				// do nothing
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void connectToOtherPeers(ArrayList<Document> peers) {
		if (peers.size() == 0) {
			return;
		}

		for (Document peer : peers) {
			targetHostPort = new HostPort(peer);
			if (!connected) {
				makeConnection();
			}
		}
	}

	private void handShakeToPeer(HostPort thp) {
		try {
			log.info("Sending handshake to : " + thp.toString());
			byte[] handShakeReq = Protocol.createHandshakeRequestP(localHostPort).getBytes("UTF-8");
			send = new DatagramPacket(handShakeReq, handShakeReq.length, InetAddress.getByName(thp.host), thp.port);
			ds.send(send);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void sendToServer(String msg) {
		try{
			
			if (connected) {
				// send msg to connected host port
				byte[] data = msg.getBytes("UTF-8");
				send = new DatagramPacket(data, data.length, connectedHp);
				ds.send(send);
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void broadcastSyncEvent() {
		while (connected) {
				log.info("Sending synchronize event to server peer");
				for (FileSystemEvent event : handler.fileSystemManager.generateSyncEvents()) {
					sendToServer(handler.toJson(event));
				}
				try {
					Thread.sleep(1000 * SYNCINTERVAL);
				} catch (InterruptedException e) {
					log.warning(e.getMessage());
				}
		}
	}
}
