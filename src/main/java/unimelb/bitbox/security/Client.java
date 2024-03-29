package unimelb.bitbox.security;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.Protocol;

/**
 * This class send instructions to Server.
 * 
 * @author: Xueying Wang
 */
public class Client {
    BufferedReader in;
    BufferedWriter out;
    protected Socket socket;
    private static final String RSA_FILE = "bitboxclient_rsa";
    private static SecretKeySpec secretKey;
    private static Logger log = Logger.getLogger(Client.class.getName());

    public static void main(String[] args) {
        Client client = new Client();
        Options options = new Options();
        options.addOption("c", true, "command");
        options.addOption("s", true, "the server's address and port number");
        options.addOption("i", true, "identity of the client");
        options.addOption("p", true, "peer address and port number");
        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            String server = cmd.getOptionValue('s');
            String command = cmd.getOptionValue('c');
            String identity = cmd.getOptionValue('i');
            String peer = cmd.getOptionValue('p');
            if (server == null || command == null || identity == null) {
                log.severe("-s, -c and -i should be identified");
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("java -cp bitbox.jar unimelb.bitbox.Client -i[IDENTITY] -c [COMMAND] -s [SERVER] -p [PEER]", options);
            }
            HostPort hostport = new HostPort(server);
            client.initConnection(hostport, identity);
            String request = generateRequest(command, peer);
            log.info("sending: " + request);
            client.out.write(request);
            client.out.flush();
            String resp = SecurityUtil.decrypt(client.in.readLine(), secretKey);
            log.info("original message: " + resp);
        } catch (ParseException e) {
            log.severe(e.getMessage());
        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }

    /**
     * create socket to connect to the Peer, send authorization request to the Peer,
     * and get the secret key.
     * 
     * @param hostport the server to connect to
     * @param identity identity of the client itself
     */
    private void initConnection(HostPort hostport, String identity) {
        try {
            socket = new Socket(hostport.host, hostport.port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            String request = Protocol.CreateAuthRequest(identity);
            out.write(request);
            out.flush();
            log.info("sending: " + request);
            String secretKey = in.readLine();
            log.info("received: " + secretKey);
            getSecretKey(secretKey);
        } catch (UnknownHostException e) {
            log.severe(e.getMessage());
        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }

    /**
     * extract the secret key from message received from server, and store it into
     * the variable.
     * 
     * @param json from server
     */
    private void getSecretKey(String json) {
        Document msg = Document.parse(json);
        String encoded = msg.getString("AES128");
        if (encoded == null)
            System.exit(1);
        Decoder decoder = Base64.getDecoder();
        byte[] encrypted = decoder.decode(encoded);
        PrivateKey privateKey = readPrivateKey(RSA_FILE);
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            secretKey = new SecretKeySpec(cipher.doFinal(encrypted), "AES");
        } catch (IllegalBlockSizeException e) {
            log.severe(e.getMessage());
        } catch (BadPaddingException e) {
            log.severe(e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            log.severe(e.getMessage());
        } catch (NoSuchPaddingException e) {
            log.severe(e.getMessage());
        } catch (InvalidKeyException e) {
            log.severe(e.getMessage());
        }

    }

    /**
     * read the private key from bitboxclient_rsa
     * 
     * @param privateKeyFileName
     * @return PrivateKey object
     */
    private PrivateKey readPrivateKey(String privateKeyFileName) {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        File privateKeyFile = new File(privateKeyFileName); // private key file in PEM format
        PEMParser pemParser;
        KeyPair kp = null;
        try {
            pemParser = new PEMParser(new FileReader(privateKeyFile));
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            kp = converter.getKeyPair((PEMKeyPair) object);
        } catch (FileNotFoundException e) {
            log.severe(e.getMessage());
        } catch (IOException e) {
            log.severe(e.getMessage());
        }
        return kp.getPrivate();

    }

    /**
     * generate json String of the request
     * 
     * @param command
     * @param peer    peer to connect or disconnect.
     * @return json string
     */
    public static String generateRequest(String command, String peer) {
        Document json = new Document();
        String cmd = null;
        switch (command) {
        case "list_peers":
            cmd = "LIST_PEERS_REQUEST";
            break;
        case "connect_peer":
            cmd = "CONNECT_PEER_REQUEST";
            HostPort hp = new HostPort(peer);
            try {
                json.append("host", InetAddress.getByName(hp.host).getHostAddress());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            json.append("port", hp.port);
            break;
        case "disconnect_peer":
            cmd = "DISCONNECT_PEER_REQUEST";
            HostPort h = new HostPort(peer);
            try {
                json.append("host", InetAddress.getByName(h.host).getHostAddress());
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
            json.append("port", h.port);
            break;
        default:
            log.severe("Invalid command. Should be one of list_peers, connect_peer, disconnect_peer");
            System.exit(1);
            break;
        }
        json.append("command", cmd);
        return SecurityUtil.encrypt(json.toJson(), secretKey) + System.lineSeparator();
    }

}
