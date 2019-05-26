package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import unimelb.bitbox.tcp.Connection;
import unimelb.bitbox.tcp.TCPClient;
import unimelb.bitbox.tcp.TCPServer;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;
import unimelb.bitbox.util.SSHEncodedToRSAPublicConverter;
import unimelb.bitbox.util.SecurityUtil;

/**
 * @author : Xueying Wang
 */
public class Server {
    ServerSocket securitySocket;
    BufferedReader in;
    BufferedWriter out;
    private static final int CLIENT_PORT = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
    private static final String[] KEYS = Configuration.getConfigurationValue("authorized_keys").split(",");
    private SecretKey secretKey;
    private MessageHandler handler;
    Socket socket;

    public Server(MessageHandler handler) {
        this.handler = handler;
        try {
            securitySocket = new ServerSocket(CLIENT_PORT);
            while (true) {
                socket = securitySocket.accept();
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                String authrequest = in.readLine();
                System.out.println(authrequest);
                Document auth = Document.parse(authrequest);
                String identity = auth.getString("identity");
                out.write(generateAuthResponse(identity));
                out.flush();
                String command = in.readLine();
                System.out.println(command);
                if (command != null) {
                    String json = SecurityUtil.decrypt(command, secretKey);
                    System.out.println(json);
                    String resp = handleCmd(json);
                    String payload = SecurityUtil.encrypt(resp, secretKey);
                    out.write(payload);
                    out.flush();

                }
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String generateAuthResponse(String identity) {
        Document doc = new Document();
        doc.append("command", "AUTH_RESPONSE");
        String message = "public key not found";
        boolean status = false;
        for (String key : KEYS) {
            String[] pub = key.split(" ");
            if (pub[2].equals(identity)) {
                message = "public key found";
                status = true;
                try {
                    System.out.println(pub[1]);
                    KeySpec spec = new SSHEncodedToRSAPublicConverter(key).convertToRSAPublicKey();
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    PublicKey pkey = kf.generatePublic(spec);
                    doc.append("AES128", encryptSecretKey(pkey));
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                } catch (InvalidKeySpecException e) {
                    e.printStackTrace();
                }
            }

        }
        doc.append("message", message);
        doc.append("status", status);
        return doc.toJson() + System.lineSeparator();

    }

    private String encryptSecretKey(PublicKey publicKey) {
        Cipher cipher;
        String encoded = null;
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(128);
            SecretKey secretKey = generator.generateKey();
            this.secretKey = secretKey;
            cipher = Cipher.getInstance("RSA");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] bytes = cipher.doFinal(secretKey.getEncoded());
            Encoder encoder = Base64.getEncoder();
            encoded = encoder.encodeToString(bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        }
        return encoded;
    }

    private String handleCmd(String json) {
        Document doc = Document.parse(json);
        String cmd = doc.getString("command");
        Document resp = null;
        if (cmd == null) {
            return Protocol.createInvalidP("invalid command");
        }
        switch (cmd) {
        case "LIST_PEERS_REQUEST":
            resp = listPeers();
            break;
        case "CONNECT_PEER_REQUEST":
            resp = connect(doc);
            break;
        case "DISCONNECT_PEER_REQUEST":
            resp = disconnect(doc);
            break;
        }
        return resp.toJson() + System.lineSeparator();
    }

    private Document listPeers() {
        Document resp = new Document();
        String command = "LIST_PEERS_RESPONSE";
        ArrayList<Document> peers = new ArrayList<Document>();
        HashMap<Connection, HostPort> serverConnections = TCPServer.connections;
        for (Connection con : serverConnections.keySet()) {
            peers.add(serverConnections.get(con).toDoc());
        }
        HashMap<Socket, HostPort> clientConnections = TCPClient.connections;
        for (Socket con : clientConnections.keySet()) {
            peers.add(clientConnections.get(con).toDoc());
        }
        for (String peer : ServerMain.PEERS) {
            peers.add(new HostPort(peer).toDoc());
        }
        resp.append("peers", peers);
        resp.append("command", command);
        return resp;
    }

    private Document connect(Document json) {
        String command = "CONNECT_PEER_RESPONSE";
        json.replace("command", command);
        HostPort hp = new HostPort(json);
        TCPClient client = new TCPClient(hp.toString(), handler);
        boolean status = false;
        String msg = null;
        if (client.connected) {
            status = true;
            msg = "connected to peer";
        } else {
            status = false;
            msg = "connection failed";
        }
        json.append("status", status);
        json.append("message", msg);
        return json;
    }

    private Document disconnect(Document json) {
        String command = "DISCONNECT_PEER_RESPONSE";
        json.replace("command", command);
        HostPort h = new HostPort(json);
        boolean status = false;
        String msg = null;
        HashMap<Connection, HostPort> serverConnections = TCPServer.connections;
        HashMap<Socket, HostPort> clientConnections = TCPClient.connections;
        if (!serverConnections.containsValue(h) && !clientConnections.containsValue(h)) {
            status = false;
            msg = "connection not active";
        }

        else {
            for (Connection con : serverConnections.keySet()) {
                if (serverConnections.get(con).equals(h)) {
                    try {
                        con.getSocket().close();
                        con.connected = false;
                        serverConnections.remove(con);
                        status = true;
                        msg = "disconnected from peer";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            for (Socket con : clientConnections.keySet()) {
                if (clientConnections.get(con).equals(h)) {
                    try {
                        con.close();
                        clientConnections.remove(con);
                        status = true;
                        msg = "disconnected from peer";
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        json.append("status", status);
        json.append("message", msg);
        return json;
    }
}