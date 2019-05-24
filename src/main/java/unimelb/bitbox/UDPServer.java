package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
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
//	private static List<HostPort> rememberedClients = new ArrayList<HostPort>();
	
	/**
	 *  A HashMap store every accepted peers in the form of <HostAddr, PortNum>,
	 *  The reason for choosing this format is that it is essential to check every incoming DatagramPacket's
	 *  hostport to ignore other peers which haven't made handshake requests with the local peer.
	 *  Using HashMap structure can make every query(check whether the hostport is remembered)
	 *  more efficient (O(1)), faster than traversing a list of HostPort objects. The value indicates the
	 *  advertised port when the handshake request has a different port number with the real port number,
	 *  default -1 otherwise.
	 */
	private static HashMap<String, Integer> rememberedPeers = new HashMap<String, Integer>();
	private static ArrayList<HostPort> candidates = new ArrayList<HostPort>();
	private MessageHandler handler;
	private DatagramSocket ds;
	private DatagramPacket received;
	private DatagramPacket send;
	private byte[] data;
	private int clientCount;

	public UDPServer(int udpPort, MessageHandler handler) {
		try {
			ds = new DatagramSocket(udpPort);
			this.handler = handler;
			clientCount = 0;
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
					String incomingHost = received.getAddress().getHostAddress();
					int incomingPort = received.getPort();
					HostPort incomingHostPort = new HostPort(incomingHost, incomingPort);
					if (rememberedPeers.get(incomingHostPort.toString()) != null) {
						new Thread(new UDPThread(received, ds, handler)).start();
					}else {
//						if (clientCount < MAXCONNECTIONS) {
						Document json = (Document) Document.parse(new String(received.getData(),0,received.getLength(),"UTF-8"));
						if (json.getString("command").equals("HANDSHAKE_REQUEST")) {
							if (clientCount < MAXCONNECTIONS) {
								if (handleHandshake(json, incomingHostPort)) {
									clientCount++;
									System.out.println("Now we have "+rememberedPeers.toString());
								}
							}else {
								log.info("connections reached max, please try connecting other peers");
//								byte[] conRefused = Protocol.createConnectionRefusedP(new ArrayList<HostPort>(UDPServer.rememberedClients)).getBytes();
								byte[] conRefused = Protocol.createConnectionRefusedP(candidates).getBytes("UTF-8");
								send = new DatagramPacket(conRefused, conRefused.length, received.getAddress(), received.getPort());
								try {
									ds.send(send);
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
//						}
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
	public boolean handleHandshake(Document msg, HostPort realHostPort) {
		// if server received a handshake request, response to the sender a
		// handshake response
		Document advertisedHostPortDoc = (Document) msg.get("hostPort");
		String advertisedHost;
		try {
			advertisedHost = InetAddress.getByName(advertisedHostPortDoc.getString("host")).getHostAddress();
			String realHost = realHostPort.host;
			int realPort = realHostPort.port;
			int advertisedPort = (int)advertisedHostPortDoc.getLong("port");
			HostPort advertisedHostPort = new HostPort(advertisedHost, advertisedPort);
			
			// Check whether the advertised host address and real host address are same
			if(!advertisedHost.equals(realHost)) {
				log.warning("Fake ip address: Told: "+advertisedHost+" but it is: "+realHost);
				// Send invalid protocol back
				try {
					byte[] fakeIP = Protocol.createInvalidP("Real host address is not same as the advertised one.").getBytes("UTF-8");
					log.info("Sent invalid protocol to: "+advertisedHostPort.toString()+" because of fake ip address");
					ds.send(new DatagramPacket(fakeIP, fakeIP.length, InetAddress.getByName(advertisedHost), advertisedPort));
					return false;
				}catch(IOException ioe) {
					ioe.printStackTrace();
				}
			}

			log.info("received HANDSHAKE_REQUEST from " + advertisedHostPort.toString() + "(" + realHostPort.toString() + ")");
			
			int additionalPort = -1;
			if (realPort != advertisedPort) {
				additionalPort = advertisedPort;
			}
			rememberedPeers.put(realHostPort.toString(), additionalPort);
			candidates.add(new HostPort(realHost, advertisedPort));
			

			byte[] handShakeResp = Protocol.createHandshakeResponseP(realHostPort).getBytes();
			send = new DatagramPacket(handShakeResp, handShakeResp.length, received.getAddress(), received.getPort());
			log.info("sending HANDSHAKE_RESPONSE to "+ realHostPort.toString());
			try {
				ds.send(send);
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			log.warning(e.getMessage());
			return false;
		}
		
		
//		if (rememberedClients.size() == MAXCONNECTIONS) {
//			log.info("connections reached max, please try connecting other peers");
//			byte[] conRefused = Protocol.createConnectionRefusedP(new ArrayList<HostPort>(UDPServer.rememberedClients)).getBytes();
//			send = new DatagramPacket(conRefused, conRefused.length, received.getAddress(), received.getPort());
//			try {
//				ds.send(send);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}else {
//			if (!rememberedClients.contains(hp) && !Arrays.asList(ServerMain.PEERS).contains(hp.toString())){
//				rememberedClients.add(hp);
//			}
//			byte[] handShakeResp = Protocol.createHandshakeResponseP(hp).getBytes();
//			send = new DatagramPacket(handShakeResp, handShakeResp.length, received.getAddress(), received.getPort());
//			log.info("sending HANDSHAKE_RESPONSE to "+ hp.toString());
//			try {
//				ds.send(send);
//			} catch (IOException e) {
//				e.printStackTrace();
//			}
//		}

	}

	public void sendToClients(String msg) {
//		synchronized (rememberedClients) {
		// There is no connection in udp mode, so server cannot know whether a client is dead or not.
		// So there is no need to dynamically update the remember list, then the synchronized keyword is useless.
//		if (rememberedClients.isEmpty()) {
		if (rememberedPeers.isEmpty()) {
			return;
		}
//		for (HostPort hp : rememberedClients) {
		for (String str : rememberedPeers.keySet()) {
			try {
				data = msg.getBytes("UTF-8");
				HostPort hp = new HostPort(str);
				send = new DatagramPacket(data, data.length, InetAddress.getByName(hp.host), hp.port);
				ds.send(send);
			} catch (UnknownHostException e1) {
				log.warning(e1.getMessage());
			} catch (IOException e) {
				log.warning(e.getMessage());
			}
		}
//		}
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
