package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class UDPServer {

	private static Logger log = Logger.getLogger(UDPServer.class.getName());
	private static final int MAXCONNECTIONS = Integer
			.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
	private static List<HostPort> rememberedClients = new ArrayList<HostPort>();

	private MessageHandler handler;
	private DatagramSocket ds;
	private DatagramPacket received;
	private DatagramPacket send;
	private byte[] data;

	public UDPServer(int udpPort, MessageHandler handler) {
		try {
			ds = new DatagramSocket(udpPort);
			this.handler = handler;
			// ds.setSoTimeout(ServerMain.UDPTIMEOUT);
		} catch (SocketException e) {
			log.warning(e.getMessage());
		}

		Runnable listener = () -> {
			try {
				while (true) {
					data = new byte[8192];
					received = new DatagramPacket(data, data.length);
					ds.receive(received); // receive packet from client
					
					if (data.length > 0) {
						Document json = (Document) Document.parse(new String(received.getData(),0,received.getLength()));
						String cmd = json.getString("command");
						if (cmd != null && cmd.equals("HANDSHAKE_REQUEST")) {
							handleHandshake(json);
						}else {
							// create a new thread to handle incoming msg
							new Thread(new UDPThread(received, ds, handler)).start();
						}
					}
				}
			} catch (IOException e) {
				log.warning(e.getMessage());
			}
		};
		new Thread(listener).start();
		new Thread(() -> broadcastSyncEvent()).start();
	}

	// public boolean isRemembered(DatagramPacket received) {
	// HostPort hp = new HostPort(received.getAddress().getHostAddress(),
	// received.getPort());
	// return rememberedClients.contains(hp);
	// }

	/**
	 * when received a DatagramPacket, need to check if it is a
	 * HANDSHAKE_REQUEST
	 * 
	 * @param received
	 * @throws IOException
	 */
	public void handleHandshake(Document msg) {
		// if server received a handshake request, response to the sender a
		// handshake response
		Document hostPort = (Document) msg.get("hostPort");
		HostPort hp = new HostPort(hostPort.getString("host"), (int)hostPort.getLong("port"));
		
		log.info("received HANDSHAKE_REQUEST from " + hp.toString());
		
		if (rememberedClients.size() == MAXCONNECTIONS) {
			log.info("connections reached max, please try connecting other peers");
			byte[] conRefused = Protocol.createConnectionRefusedP(new ArrayList<HostPort>(UDPServer.rememberedClients)).getBytes();
			send = new DatagramPacket(conRefused, conRefused.length, received.getAddress(), received.getPort());
			try {
				ds.send(send);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}else {
			if (!rememberedClients.contains(hp) && !Arrays.asList(ServerMain.PEERS).contains(hp.toString())){
				rememberedClients.add(hp);
			}
			byte[] handShakeResp = Protocol.createHandshakeResponseP(hp).getBytes();
			send = new DatagramPacket(handShakeResp, handShakeResp.length, received.getAddress(), received.getPort());
			log.info("sending HANDSHAKE_RESPONSE to "+ hp.toString());
			try {
				ds.send(send);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	public void sendToClients(String msg) {
		synchronized (rememberedClients) {
			if (rememberedClients.isEmpty()) {
				return;
			}
			for (HostPort hp : rememberedClients) {
				
				try {
					data = msg.getBytes();
					send = new DatagramPacket(data, data.length, InetAddress.getByName(hp.host), hp.port);
					ds.send(send);
				} catch (UnknownHostException e1) {
					log.warning(e1.getMessage());
				} catch (IOException e) {
					log.warning(e.getMessage());
				}
			}
		}
	}
	
	private void broadcastSyncEvent() {
			log.info("Sending synchronize event to client peer");
			for (FileSystemEvent event : handler.fileSystemManager.generateSyncEvents()) {
				sendToClients(handler.toJson(event));
			}
			try {
				Thread.sleep(1000 * SYNCINTERVAL);
			} catch (InterruptedException e) {
				log.warning(e.getMessage());
			}
	}
}
