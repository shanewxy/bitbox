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
    private Map<HostPort, List<String>> responseStatus = new HashMap();
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
        new Thread(() -> receiveMsg()).start();
        log.info("Start receiving messages thread...");

        makeConnections(peers);

    }

    public boolean makeConnections(String[] peers) throws UnsupportedEncodingException {
        boolean status = false;
        log.info("Start connecting with provided peers...");
        for (String peer : peers) {
            HostPort targetHostPort = new HostPort(peer);
            byte[] hsRequest = Protocol.createHandshakeRequestP(localHostPort).getBytes("UTF-8");
            try {
                status = sendToPeer(Protocol.createHandshakeRequestP(localHostPort), targetHostPort);
//                socket.send(new DatagramPacket(hsRequest, hsRequest.length, InetAddress.getByName(targetHostPort.host), targetHostPort.port));
                HostPort hostPort = new HostPort(InetAddress.getByName(targetHostPort.host).getHostAddress(), targetHostPort.port);
                rememberedPeers.put(hostPort.toString(), -1);
                if (status)
                    candidates.add(hostPort);
                new Thread(() -> broadcastSyncEvents()).start();
                log.info("Start synchronized events broadcasting thread...");
            } catch (UnknownHostException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
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
                if (command.endsWith("_RESPONSE")) {
                    for (HostPort hp : responseStatus.keySet()) {
                        if (hp.equals(incomingHP)) {
                            List<String> list = responseStatus.get(hp);
                            String replace = command.replace("_RESPONSE", "");
                            list.remove(replace);
                        }
                    }
                }

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
//				if (rememberedPeers.get(incomingHP.toString()) != null || clientCount < MAXCONNECTIONS) {
//					new ProcessMessages(payload).start();
//				}else {
//					// If the peer of this incoming packet hasn't been remembered and the local maximum connection limitation is hit, ignore it
//					continue;
//				}

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

    public boolean sendToPeer(String msg, HostPort hp) {
        boolean status = false;
        int attempts = 1;
        try {
            InetAddress address = InetAddress.getByName(hp.host);
            byte[] bytesMsg = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(bytesMsg, bytesMsg.length, address, hp.port);
            List<String> requests = responseStatus.getOrDefault(hp, new ArrayList());
            String rawMsg = Document.parse(msg).getString("command").replace("_REQUEST", "");
            requests.add(rawMsg);
            hp = new HostPort(address.getHostAddress(), hp.port);
            responseStatus.put(hp, requests);
            while (responseStatus.get(hp).contains(rawMsg) && attempts <= UDPATTEMPTS) {
                log.info(msg + "trying no." + attempts++);
                socket.send(packet);
                Thread.sleep(UDPTIMEOUT);
            }
            if (attempts > UDPATTEMPTS)
                log.warning("peer failed");
            else {
                log.info(msg + " sent successfully");
                status = true;
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return status;

    }

    private class ProcessMessages extends Thread {

        private String msg;
        private boolean emptySlots = true;
        private HostPort targetHP;
//		private String targetIP;
//		private int targetPort;
//		private InetAddress targetInetAddress;
        private DatagramPacket payload;

        private ProcessMessages(DatagramPacket payload) {

            this.payload = payload;

//			this.targetPort = payload.getPort();
//			this.targetInetAddress = payload.getAddress();
//			this.targetIP = this.targetInetAddress.getHostAddress();
            targetHP = new HostPort(payload.getAddress().getHostAddress(), payload.getPort());
            if (clientCount == MAXCONNECTIONS) {
                emptySlots = false;
            }
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
//				payload.setData(buf);
//				socket.send(new DatagramPacket(handShakeResp, handShakeResp.length, InetAddress.getByName(realHostPort.host), realHostPort.port));
                byte[] handShakeResp;
                try {
                    handShakeResp = Protocol.createHandshakeResponseP(localHostPort).getBytes("UTF-8");
                    payload.setData(handShakeResp);
                    payload.setLength(handShakeResp.length);
                    socket.send(payload);
                    return;
                } catch (UnsupportedEncodingException uee) {
                    // TODO Auto-generated catch block
                    uee.printStackTrace();
                } catch (IOException ioe) {
                    // TODO Auto-generated catch block
                    ioe.printStackTrace();
                }
            }
            if (msg != null) {

                List<Document> responses = handler.handleMsg(msg);
                try {
                    if (responses != null) {
                        for (Document d : responses) {
                            byte[] data = d.toJson().getBytes("UTF-8");
                            payload.setData(data);
                            payload.setLength(data.length);
                            socket.send(payload);
                            log.info("Sent responce: " + d.toJson());
                        }
                    }
                } catch (IOException e) {
                    log.warning("Exception when sending packet: " + e.getMessage());
                }
            }
        }
    }
}
