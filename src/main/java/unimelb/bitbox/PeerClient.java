package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

/**
 * The component of a peer that aims to handle communications between the local
 * peer and the remote peer that we started to connect with
 * 
 * @author Xueying Wang
 * @author Yichen Liu
 */
public class PeerClient implements Runnable {
    private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
    public static HashMap<Socket, HostPort> connections = new HashMap<Socket, HostPort>();

    BufferedReader in;
    BufferedWriter out;
    public boolean connected = false;
    private Socket s;

    private MessageHandler handler;

    /**
     * The ip address and port number of the peer that this client currently want to
     * connect with
     */
    private HostPort targetHostPort;

    /**
     * The ip address and port number of the local peer
     */
    private static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));

    private static Logger log = Logger.getLogger(MessageHandler.class.getName());

    /**
     * The client object will try to connect with target peer when created, if
     * connection successfully established, a new thread will be created for
     * handling incoming messages.
     * 
     * @param peer    the ip address and port number of the peer we want to connect
     * @param handler the message handler object
     */
    public PeerClient(String peer, MessageHandler handler) {
        this.handler = handler;
        targetHostPort = new HostPort(peer);

        try {
            s = new Socket(targetHostPort.host, targetHostPort.port);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));

            log.info("Initial socketd established: " + targetHostPort.toString());
            if (initConnection(s)) {
                connected = true;
                connections.put(s, targetHostPort);
                new Thread(this).start();
            }
        } catch (UnknownHostException e) {
            log.warning("Sock:" + e.getMessage());
        } catch (EOFException e) {
            log.warning("EOF:" + e.getMessage());
        } catch (IOException e) {
            log.warning("IO:" + e.getMessage() + targetHostPort.toString());
        }
    }

    /**
     * send message to server.
     */
    public void sendToServer(String msg) {
        try {
            if (connected) {
                synchronized (out) {
                    out.write(msg + System.lineSeparator());
                    out.flush();
                }

            }
        } catch (SocketException e) {
            connected = false;
            connections.remove(s);
        } catch (IOException e) {
            log.warning(e.getMessage());
        }
    }

    @Override
    public void run() {
        while (true) {
            String data = null;
            try {
                data = in.readLine();
                if (data == null) {
                    log.info("Connection closed by server: " + targetHostPort.toString());
                    in.close();
                    out.close();
                    s.close();
                    connected = false;
                    connections.remove(s);
                    return;
                }
                List<Document> responses = handler.handleMsg(data);
                if (responses != null) {
                    for (Document r : responses) {
                        sendToServer(r.toJson());
                    }
                }
            } catch (SocketException e) {
                log.severe(e.getMessage());
                break;
            } catch (IOException e) {
                log.warning(e.getMessage());
            }
        }
    }

    /**
     * Validate whether the handshake response is valid or not
     * 
     * @param proto the handshake response
     * @return 1 the response is valid with a valid hostPort; 0 the response is a
     *         connection refused protocol; -1 the response is invalid;
     */
    @SuppressWarnings("unused")
    private int validateInitialProtocol(Document proto) {
        try {
            // The name of command written in the validating protocol
            String commandField = proto.getString("command");

            if (commandField != null) {
                if (commandField.equals("HANDSHAKE_RESPONSE")) {
                    // An exception would be catch if something goes wrong while
                    // parsing the hostPort field of the protocol
                    HostPort hpField = new HostPort((Document) proto.get("hostPort"));
                    return 1;
                } else if (commandField.equals("CONNECTION_REFUSED")) {
                    return 0;
                } else {
                    return -1;
                }
            } else {
                return -1;
            }
        } catch (Exception e) {
            // Any exception happened during parsing the protocol will make this
            // protocol invalid
            return -1;
        }
    }

    @SuppressWarnings("unchecked")
    public Boolean initConnection(Socket client) {

        try {
            log.info("Sending handshake request...");
            out.write(Protocol.createHandshakeRequestP(localHostPort));
            out.flush();

            String response = in.readLine();
            log.info("Handshake response: " + response);
            Document receivedCommand = Document.parse(response);
            switch (validateInitialProtocol(receivedCommand)) {
            case -1:
                log.info("Received invalid protocol");
                out.write(Protocol.createInvalidP("Invalid Message"));
                out.flush();
                client.close();
                return false;
            case 0:
                log.info("The remote peer is already full, try other peers that provided by the remote one");
                client.close();

                return connectOtherPeers((ArrayList<Document>) receivedCommand.get("peers"));
            case 1:
                log.info("Successfully connected with peer: " + targetHostPort.toString());
                new Thread(() -> broadcastSyncEvent()).start();
                // Send syncEvents to server peer when connection established
                return true;
            }

        } catch (UnknownHostException e) {
            log.warning(e.getMessage());
            return false;
        } catch (IOException e) {
            log.warning("Connection failed with remote peer: " + targetHostPort.toString());
            return false;
        }
        return false;
    }

    /**
     * The method used to connect with other provided peers when a connection
     * request is refused. This method may be recursively called if some of the the
     * provided peers are full as well. The main algorithm of trying to connect with
     * other peers follows the breadth first search principle.
     * 
     * @param peers a list of peer candidates provided by previous full peer
     * @return True if one connection is granted; otherwise false
     */
    public Boolean connectOtherPeers(ArrayList<Document> peers) {
        if (peers.size() == 0) {
            log.info("Recieved empty peer list");
            return false;
        }

        for (Document peer : peers) {
            targetHostPort = new HostPort(peer);
            try {
                log.info("Now trying other peers");
                s = new Socket(targetHostPort.host, targetHostPort.port);
                log.info("Initial socket established: " + targetHostPort.toString());
                in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
                out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));

                return initConnection(s);
            } catch (UnknownHostException e) {
                log.warning("Invalid hostPort: " + targetHostPort.toString() + "///" + e.getMessage());
            } catch (IOException e) {
                log.warning("Failed to create socket: " + targetHostPort.toString() + "///" + e.getMessage());
            }
        }

        return false;
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