package unimelb.bitbox;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

/**
 * Unlike the TCP mode, UDP mode unifies both server and client and forms an
 * agent handling receiving/sending messages, which is more like a peer.
 * However, only those remembered peers(which successfully passed through the
 * handshake process) are able to communicate with the local peer.
 * 
 * @author Kiwilyc
 * @author Xueying Wang
 *
 */
public class UDPAgent {
    private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
    private static final int MAXCONNECTIONS = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
    private static final int UDPTIMEOUT = Integer.parseInt(Configuration.getConfigurationValue("udpTimeOut"));
    private static final int UDPATTEMPTS = Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));
    private static Logger log = Logger.getLogger(UDPAgent.class.getName());

    private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("udpPort")));
    public static HashMap<String, Integer> rememberedPeers = new HashMap<String, Integer>();
    public static ArrayList<HostPort> candidates = new ArrayList<HostPort>();
//    private Map<HostPort, List<String>> responseStatus = new HashMap();
    private Map<String, ArrayList<String>> timeoutCollections = new ConcurrentHashMap<String, ArrayList<String>>();
    private DatagramSocket socket;
    private MessageHandler handler;
    private int clientCount;
    private static UDPAgent instance;

    private UDPAgent(int udpPort, MessageHandler handler, String[] peers) throws UnsupportedEncodingException {

        this.handler = handler;
        clientCount = 0;
        try {
            socket = new DatagramSocket(udpPort);
            log.info("Succesfully created datagram socket on port: " + udpPort);
        } catch (SocketException se) {
            log.warning("Failed to create Datagram Socket: " + se.getStackTrace());
        }
        
        // Adding peers of the peer list into the rememberedPeer HashMap
        for (String peer : peers) {
        	String[] hpStr = peer.split(":");
        	try {
				rememberedPeers.put(new HostPort(InetAddress.getByName(hpStr[0]).getHostAddress(), Integer.parseInt(hpStr[1])).toString(), -1);
			} catch (NumberFormatException e) {
				log.warning("Invalid hostport when adding peers of the provided peer list");
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
        }
        
        new Thread(() -> receiveMsg()).start();
        log.info("Start receiving messages thread...");

        makeConnections(peers);

    }

    public boolean makeConnections(String[] peers) throws UnsupportedEncodingException {
        boolean status = false;
        log.info("Start connecting with provided peers...");
        Document hsRequest = Document.parse(Protocol.createHandshakeRequestP(localHostPort));
        byte[] data = hsRequest.toJson().getBytes("UTF-8");
        DatagramPacket payload = new DatagramPacket(data, data.length);
        for (String peer : peers) {
        	String[] hpStr = peer.split(":");
            try {
            	HostPort targetHostPort = new HostPort(InetAddress.getByName(hpStr[0]).getHostAddress(), Integer.parseInt(hpStr[1]));
				payload.setAddress(InetAddress.getByName(targetHostPort.host));
				payload.setPort(targetHostPort.port);
                status = reliableSend(hsRequest, payload, targetHostPort.toString());
                if(status) {
                	HostPort newHostPort = new HostPort(targetHostPort.host, targetHostPort.port);
                    rememberedPeers.put(newHostPort.toString(), -1);
                    candidates.add(newHostPort);
//                    new Thread(() -> broadcastSyncEvents()).start();
//                    log.info("Start synchronized events broadcasting thread...");
                    break;
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!status) {
        	log.warning("Failed to connect with any peer in the list: "+peers.toString());
        }
        return status;
    }

    private void receiveMsg() {

        while (true) {
            byte[] data = new byte[12000];
            DatagramPacket payload = new DatagramPacket(data, data.length);
            try {
                socket.receive(payload);
                HostPort incomingHP = new HostPort(payload.getAddress().getHostAddress(), payload.getPort());
                String json = new String(payload.getData(), 0, payload.getLength(), "UTF-8");
                Document messageDoc = (Document) Document.parse(json);
                String command = messageDoc.getString("command");
//                if (command.endsWith("_RESPONSE")) {
//                    for (HostPort hp : responseStatus.keySet()) {
//                        if (hp.equals(incomingHP)) {
//                            List<String> list = responseStatus.get(hp);
//                            String replace = command.replace("_RESPONSE", "");
//                            list.remove(replace);
//                        }
//                    }
//                }

                if (rememberedPeers.get(incomingHP.toString()) != null) {
                    new ProcessMessages(payload).start();
                } else {
                    if (command.equals("HANDSHAKE_REQUEST")) {
                        if (clientCount < MAXCONNECTIONS) {
                            if (handleHandshake(new HostPort((Document) messageDoc.get("hostPort")), incomingHP)) {
                                clientCount++;
                                System.out.println("Now we have " + rememberedPeers.toString());
                            }
                        } else {
                            log.info("connections reached max, please try connecting other peers");
                            byte[] conRefused = Protocol.createConnectionRefusedP(candidates).getBytes("UTF-8");
                            payload.setData(conRefused);
                            payload.setLength(conRefused.length);
                            try {
                                socket.send(payload);
                            } catch (IOException e) {
                                log.info("Exception when sending handshake response: " + e.getStackTrace());
                            }
                        }
                    }
                }

            } catch (IOException e) {
                log.warning("Exception when receiving messages: " + e.getMessage());
            }
        }

    }

    private boolean handleHandshake(HostPort advertisedHostPort, HostPort realHostPort) throws UnsupportedEncodingException {

        log.info("received HANDSHAKE_REQUEST from " + advertisedHostPort.toString() + "(" + realHostPort.toString() + ")");
        try {
            String advertisedHost = InetAddress.getByName(advertisedHostPort.host).getHostAddress();
            String realHost = realHostPort.host;
            int realPort = realHostPort.port;
            int advertisedPort = advertisedHostPort.port;

            // Check whether the advertised host address and real host address are same
            if (!advertisedHost.equals(realHost)) {
                log.warning("Fake ip address: Told: " + advertisedHost + " but it is: " + realHost);
                // Send invalid protocol back
                try {
                    byte[] fakeIP = Protocol.createInvalidP("Real host address is not same as the advertised one.").getBytes("UTF-8");
                    log.info("Sent invalid protocol to: " + realHostPort.toString() + " because of fake ip address");
                    socket.send(new DatagramPacket(fakeIP, fakeIP.length, InetAddress.getByName(realHost), realPort));
                    return false;
                } catch (IOException ioe) {
                    log.warning("Exception when sending invalid protocol (fake ip): " + ioe.getStackTrace());
                }
            }

            int additionalPort = -1;
            if (realPort != advertisedPort) {
                additionalPort = advertisedPort;
            }
            rememberedPeers.put(realHostPort.toString(), additionalPort);
            candidates.add(new HostPort(realHost, advertisedPort));

            byte[] handShakeResp = Protocol.createHandshakeResponseP(realHostPort).getBytes("UTF-8");
            log.info("sending HANDSHAKE_RESPONSE to " + realHostPort.toString());
            try {
                socket.send(new DatagramPacket(handShakeResp, handShakeResp.length, InetAddress.getByName(realHost), realPort));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;

        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            log.warning("" + e.getStackTrace());
            return false;
        }
    }

    public void sendToPeers(String msg) {

        if (rememberedPeers.isEmpty()) {
            log.warning("No peer is connected now, cannot send message to connected peers: " + msg);
            return;
        }
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket payload = new DatagramPacket(data, data.length);
            log.info("Start broadcasting a message to all connected peers: " + msg);
            for (String str : rememberedPeers.keySet()) {
                String[] hpStr = str.split(":");
                payload.setAddress(InetAddress.getByName(hpStr[0]));
                payload.setPort(Integer.parseInt(hpStr[1]));
                socket.send(payload);
//            	reliableSend(Document.parse(msg), payload, str);
            }
        } catch (IOException ioe) {
            log.warning("Exception when broadcasting messages: " + ioe.getStackTrace());
        }
    }

    private void broadcastSyncEvents() {

        while (true) {
            log.info("Sending synchronize event to connected peer");
            for (FileSystemEvent event : handler.fileSystemManager.generateSyncEvents()) {
                sendToPeers(handler.toJson(event));
            }
            try {
                Thread.sleep(1000 * SYNCINTERVAL);
            } catch (InterruptedException e) {
                log.warning(e.getMessage());
            }
        }
    }

    public static synchronized UDPAgent getInstance(int udpPort, MessageHandler handler, String[] peers) {
        if (instance == null)
            try {
                instance = new UDPAgent(udpPort, handler, peers);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        return instance;
    }

//    public boolean sendToPeer(String msg, HostPort hp) {
//        boolean status = false;
//        int attempts = 1;
//        try {
//            InetAddress address = InetAddress.getByName(hp.host);
//            byte[] bytesMsg = msg.getBytes("UTF-8");
//            DatagramPacket packet = new DatagramPacket(bytesMsg, bytesMsg.length, address, hp.port);
//            @SuppressWarnings("unchecked")
//			List<String> requests = responseStatus.getOrDefault(hp, new ArrayList());
//            String rawMsg = Document.parse(msg).getString("command").replace("_REQUEST", "");
//            requests.add(rawMsg);
//            hp = new HostPort(address.getHostAddress(), hp.port);
//            responseStatus.put(hp, requests);
//            while (responseStatus.get(hp).contains(rawMsg) && attempts <= UDPATTEMPTS) {
//                log.info(msg + "trying no." + attempts++);
//                socket.send(packet);
//                Thread.sleep(UDPTIMEOUT);
//            }
//            if (attempts > UDPATTEMPTS)
//                log.warning("peer failed");
//            else {
//                log.info(msg + " sent successfully");
//                status = true;
//            }
//        } catch (UnknownHostException e) {
//            e.printStackTrace();
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return status;
//
//    }
    
    private String ExtractFeature(Document request) {
    	String command = request.getString("command");
    	String feature = "";
    	if(command.contains("FILE")) {
    		Document fileDescriptor = (Document) request.get("fileDescriptor");
    		feature = fileDescriptor.toJson() + "^" + request.getString("pathName");
    	}else if(command.contains("DIRECTORY")) {
    		feature = request.getString("pathName");
    	}else if(command.contains("HANDSHAKE")) {
    		feature = "HANDSHAKE";
    	}
    	return feature;
    }
    
    private boolean reliableSend(Document doc, DatagramPacket payload, String targetHPStr) throws UnsupportedEncodingException {
    	boolean status = true;
		log.info("I am sending a request <<"+doc.toJson()+">> to peer: "+targetHPStr);
		String feature = ExtractFeature(doc);
		ArrayList<String> oldList;
		ArrayList<String> newList = new ArrayList<String>(1);
		ArrayList<String> updateList;
		while(true) {
			oldList = timeoutCollections.get(targetHPStr);
			if(oldList == null) {
				newList.add(feature);
    			if (timeoutCollections.putIfAbsent(targetHPStr, newList) == null) {
    				break;
    			}
			}else {
				updateList = new ArrayList<String>(oldList);
				updateList.add(feature);
				if (timeoutCollections.replace(targetHPStr, oldList, updateList)) {
					break;
				}
			}
		}
        byte[] data = doc.toJson().getBytes("UTF-8");
        payload.setData(data);
        payload.setLength(data.length);
        int attempts = 0;
        do {
            attempts ++;
            try {
                socket.send(payload);
                log.info("Sent request to : " + doc.toJson() + "=> attemps" + attempts);
                Thread.sleep(UDPTIMEOUT);
            }catch (IOException ioe) {
            	log.warning("Exception when sending a request:"+ioe.getStackTrace());
            } catch (InterruptedException e) {
				e.printStackTrace();
			}
        } while(timeoutCollections.get(targetHPStr).contains(feature) || attempts < UDPATTEMPTS);
        if (attempts == UDPATTEMPTS) {
        	log.warning("Failed to send request");
        	status = false;
        }
        return status;
    }

    private class ProcessMessages extends Thread {

        private String msg;
        private HostPort targetHP;
        private String targetHPStr;
        private DatagramPacket payload;

        private ProcessMessages(DatagramPacket payload) {

            this.payload = payload;

            targetHP = new HostPort(payload.getAddress().getHostAddress(), payload.getPort());
            targetHPStr = targetHP.toString();
            try {
                msg = new String(payload.getData(), 0, payload.getLength(), "UTF-8");
                log.info("Start processing: " + msg);
            } catch (UnsupportedEncodingException e) {
                // Nearly impossible
                e.printStackTrace();
            }
        }
        


        @Override
        public void run() {

            if (Document.parse(msg).get("command").equals("HANDSHAKE_REQUEST")) {
                byte[] handShakeResp;
                try {
                    handShakeResp = Protocol.createHandshakeResponseP(localHostPort).getBytes("UTF-8");
                    payload.setData(handShakeResp);
                    payload.setLength(handShakeResp.length);
                    socket.send(payload);
                    return;
                } catch (UnsupportedEncodingException uee) {
                    uee.printStackTrace();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            if (msg != null) {
            	Document doc = Document.parse(msg);
            	if (doc.getString("command").contains("RESPONSE")) {
            		String feature = ExtractFeature(doc);
            		try {
            			if (timeoutCollections.get(targetHPStr).remove(feature)) {
            				log.info("Successfully recieved a corresponding response");
            			}
            		}catch(NullPointerException npe) {
            			log.info("Nothing here");
            		}
            	}
                List<Document> responses = handler.handleMsg(msg);
                try {
                    if (responses != null) {
                        for (Document d : responses) {
                        	if (d.getString("command").contains("REQUEST")) {
                        		reliableSend(d, payload, targetHPStr);
                        	}else {
                                byte[] data = d.toJson().getBytes("UTF-8");
                                payload.setData(data);
                                payload.setLength(data.length);
                                socket.send(payload);
                                log.info("Sent response: " + d.toJson());
                        	}
                        }
                    }
                } catch (IOException e) {
                    log.warning("Exception when sending packet: " + e.getStackTrace());
                }
            }
        }
    }
}
