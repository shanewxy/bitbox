package unimelb.bitbox.tcp;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.MessageHandler;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

/**
 * The main class of handling communications between the local peers and its
 * client peers. A connection object will be created as a thread for each
 * incoming peer connection.
 * 
 * @author Xueying Wang
 * @author Yichen Liu
 */
public class Connection extends Thread {
    private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));

    BufferedReader in;
    BufferedWriter out;
    private Socket socket;
    private MessageHandler handler;
    private HostPort clientHostPort;
    private static Logger log = Logger.getLogger(MessageHandler.class.getName());
    public boolean connected = false;

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
            // The first protocol received from a new potential client should be
            // a handshake
            // request
            Document handshake = Document.parse(in.readLine());
            log.info(handshake.toJson());
            HostPort hp = null;
            // Validate the content of this request, send invalid protocol when
            // anything is
            // invalid
            if (handshake.getString("command").equals("HANDSHAKE_REQUEST")) {
                try {
                    // Will throw a ClassCastException here if the port field is
                    // not a integer
                    clientHostPort = new HostPort((Document) handshake.get("hostPort"));
                    if (clientHostPort.host != null) {
                        if (TCPServer.clientCount.get() < TCPServer.maximumConnections) {
                            TCPServer.clientCount.getAndIncrement();
                            out.write(Protocol.createHandshakeResponseP(TCPServer.localHostPort));
                            out.flush();
                            TCPServer.connections.put(this, new HostPort(InetAddress.getByName(clientHostPort.host).getHostAddress(), clientHostPort.port));

                            this.connected = true;

                            new Thread(() -> broadcastSyncEvent()).start();
                        } else {
                            out.write(Protocol.createConnectionRefusedP(new ArrayList<HostPort>(TCPServer.connections.values())));
                            out.flush();
                            socket.close();
                            return;
                        }
                    } else {
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
            } else {
                out.write(Protocol.createInvalidP("Your first message should be a handshake request rather than any other msg!"));
                out.flush();
                socket.close();
                return;
            }

            // The main message handling process
            while (connected) {
                String msg = in.readLine();
                if (msg == null) {
                    log.warning("Connection closed remotely ");
                    socket.close();
                    TCPServer.clientCount.decrementAndGet();
                    TCPServer.connections.remove(this);
                    return;
                }
                List<Document> responses = handler.handleMsg(msg);
                if (responses != null) {
                    for (Document r : responses) {
                        synchronized (out) {
                            out.write(r.toJson() + System.lineSeparator());
                            out.flush();

                        }
                    }
                }
            }

        } catch (EOFException e) {
            log.warning("Connection closed");
        } catch (IOException e) {
            log.warning(e.getMessage());
        }

    }

    private void broadcastSyncEvent() {
        while (connected) {
            log.info("Sending synchronize event to client peer");
            for (FileSystemEvent event : handler.fileSystemManager.generateSyncEvents()) {
                try {
                    synchronized (out) {
                        out.write(handler.toJson(event) + System.lineSeparator());
                        out.flush();
                    }
                } catch (SocketException e) {
                    this.connected = false;
                    TCPServer.connections.remove(this);
                } catch (IOException e) {
                    log.warning(e.getMessage());
                }
            }
            try {
                Thread.sleep(1000 * SYNCINTERVAL);
            } catch (InterruptedException e) {
                log.warning(e.getMessage());
            }
        }
    }

    /**
     * @return the socket
     */
    public Socket getSocket() {
        return socket;
    }

}