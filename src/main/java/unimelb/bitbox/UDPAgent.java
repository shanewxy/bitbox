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
 * @author Kedi Peng
 * @author Xueying Wang
 * @author Yichen Liu
 *
 */
public class UDPAgent {
    private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
    private static final int MAXCONNECTIONS = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
    private static final int UDPTIMEOUT = Integer.parseInt(Configuration.getConfigurationValue("udpTimeOut"));
    private static final int UDPATTEMPTS = Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));
    private static Logger log = Logger.getLogger(UDPAgent.class.getName());

    private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("udpPort")));
    /**
     * This HashMap aims to store all known/connected peers, with an alternative
     * port value (default=-1), indicating the advertised port when it is different
     * from the real port
     */
    public static HashMap<String, Integer> rememberedPeers = new HashMap<String, Integer>();
    /**
     * An ArrayList storing all connected peers' HostPort
     */
    public static ArrayList<HostPort> candidates = new ArrayList<HostPort>();
    /**
     * A ConcurrentHashMap storing every sent request and its "feature", which is a
     * unique characteristic for each sent request, in order to track whether their
     * corresponding response is arrived successfully later.
     */
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

    /**
     * Try to connect one of the peers in the provided peer list, can also be used
     * to connect with specified peer by the command from client
     * 
     * @param peers The provided peer list
     * @return True if successfully connect with one of the peer in the peer list
     * @throws UnsupportedEncodingException
     */
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
                // Try to connect with a peer will automatically add it into the remembered list
                HostPort newHostPort = new HostPort(targetHostPort.host, targetHostPort.port);
                rememberedPeers.put(newHostPort.toString(), -1);
                status = reliableSend(hsRequest, payload, targetHostPort.toString());
                if (status) {

                    rememberedPeers.put(newHostPort.toString(), -1);
                    candidates.add(newHostPort);
                    new Thread(() -> broadcastSyncEvents()).start();
                    log.info("Start synchronized events broadcasting thread...");
                    break;
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (!status) {
            log.warning("Failed to connect with any peer in the list: " + peers.toString());
        }
        return status;
    }

    /**
     * Main method for handling every incoming UDP DatagramPacket on specified port
     */
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

                // First check whether the incoming packet comes from a known/connected peer
                if (rememberedPeers.get(incomingHP.toString()) != null) {
                    new ProcessMessages(payload).start();
                    // If not, check whether it is a handshake request, if it is still not a hs
                    // request, ignore it
                } else {
                    // Check the connection capacity
                    if (command.equals("HANDSHAKE_REQUEST")) {
                        if (clientCount < MAXCONNECTIONS) {
                            if (handleHandshake(new HostPort((Document) messageDoc.get("hostPort")), incomingHP)) {
                                clientCount++;
                                log.info("Currently connected peers " + candidates.toString());
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

    /**
     * When received a handshake request, process it
     * 
     * @param advertisedHostPort The HostPort that the remote peer advertises
     * @param realHostPort       The real HostPort of the remote peer by the help of
     *                           InetAddress
     * @return True if the handshake process is successful
     * @throws UnsupportedEncodingException
     */
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

    /**
     * When some file events are produced, local peer will use this method to
     * broadcast proper messages to all connected peers
     * 
     * @param msg The message that required to broadcast
     */
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

    /**
     * The singleton design
     */
    public static synchronized UDPAgent getInstance(int udpPort, MessageHandler handler, String[] peers) {
        if (instance == null)
            try {
                instance = new UDPAgent(udpPort, handler, peers);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        return instance;
    }

    /**
     * Extract a unique feature from a request or response. The feature will be the
     * same when the request and the response are corresponding. Meaning that the
     * response is belongs to that request
     * 
     * @param request
     * @return
     */
    private String ExtractFeature(Document request) {
        String command = request.getString("command");
        String feature = "";
        if (command.contains("FILE")) {
            Document fileDescriptor = (Document) request.get("fileDescriptor");
            feature = fileDescriptor.toJson() + "^" + request.getString("pathName");
        } else if (command.contains("DIRECTORY")) {
            feature = request.getString("pathName");
        } else if (command.contains("HANDSHAKE")) {
            feature = "HANDSHAKE";
        }
        return feature;
    }

    /**
     * A reliable send method that realize the re-sending functionality of requests,
     * the request will be re-send after one interval of UDPTIMEOUT when the
     * corresponding response still doesn't arrive. After the attempt time exceed
     * the UDPATTEMPTS, the sending process of this request will be regarded as
     * failure.
     * 
     * @param doc         Request document object
     * @param payload     The required DatagramPacket
     * @param targetHPStr Target HostPort in the form of string
     * @return
     * @throws UnsupportedEncodingException
     */
    private boolean reliableSend(Document doc, DatagramPacket payload, String targetHPStr) throws UnsupportedEncodingException {
        boolean status = true;
        log.info("I am sending a request <<" + doc.toJson() + ">> to peer: " + targetHPStr);
        String feature = ExtractFeature(doc);
        ArrayList<String> oldList;
        ArrayList<String> newList = new ArrayList<String>(1);
        ArrayList<String> updateList;
        while (true) {
            oldList = timeoutCollections.get(targetHPStr);
            if (oldList == null) {
                newList.add(feature);
                if (timeoutCollections.putIfAbsent(targetHPStr, newList) == null) {
                    break;
                }
            } else {
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
            attempts++;
            try {
                socket.send(payload);
                log.info("Sent request to : " + doc.toJson() + " => attempt " + attempts);
                if (!timeoutCollections.get(targetHPStr).contains(feature)) {
                    status = true;
                    break;
                }
                Thread.sleep(UDPTIMEOUT);
            } catch (IOException ioe) {
                log.warning("Exception when sending a request:" + ioe.getStackTrace());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // Check whether the feature HashMap still contain such feature, meaning the
            // response hasn't arrive
            status = false;
            log.warning("Failed to send request (attempt " + attempts + ")...");
        } while (attempts < UDPATTEMPTS);
        if (!status) {
            timeoutCollections.get(targetHPStr).remove(feature);
            log.severe("All " + UDPATTEMPTS + " attempts failed");
        }
        return status;
    }

    /**
     * An inner class acting like the thread for processing one message with the
     * help of file management system
     * 
     * @author Kedi Peng
     * @author Yichen Liu
     *
     */
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
            } catch (UnsupportedEncodingException e) {
                // Nearly impossible
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            // Tolerating the multiple appearances of one handshake request (re-sending)
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
            // Whenever receives a response, check the feature HashMap that whether the same
            // feature has already stored,
            // which means this response is corresponding with a previous sent request. If
            // so, delete that feature record,
            // which will also trigger the reliableSend method to judge that request is
            // successfully sent or not
            if (msg != null) {
                Document doc = Document.parse(msg);
                if (doc.getString("command").contains("RESPONSE")) {
                    String feature = ExtractFeature(doc);
                    try {
                        if (timeoutCollections.get(targetHPStr).remove(feature)) {
                            log.info("Successfully recieved a corresponding response");
                        }
                    } catch (NullPointerException npe) {
                        log.info("Nothing here");
                    }
                }
                // Using MessageHandler to handle messages
                List<Document> responses = handler.handleMsg(msg);
                try {
                    if (responses != null) {
                        for (Document d : responses) {
                            String retryMsg = d.getString("command");
                            if (retryMsg.contains("REQUEST") || retryMsg.contains("FILE_BYTES_RESPONSE")) {
                                reliableSend(d, payload, targetHPStr);
                            } else {
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
