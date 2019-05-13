package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;

/**
 * @author : Xueying Wang
 */
public class Server {
    ServerSocket securitySocket;
    BufferedReader in;
    BufferedWriter out;
    private static final int CLIENT_PORT = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));
    private static final String[] KEYS = Configuration.getConfigurationValue("authorized_keys").split(",");
    /**
    * 
    */
    public ServerSocket ss;

    /**
     * 
     */
    public Server() {
        try {
            securitySocket = new ServerSocket(CLIENT_PORT);
            while (true) {
                Socket socket = securitySocket.accept();
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                Document auth = Document.parse(in.readLine());
                String identity = auth.getString("identity");
                out.write(generateSecretKey(identity));
                out.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String generateSecretKey(String identity) {
        Document doc = new Document();
        doc.append("command", "AUTH_RESPONSE");
        String message = "public key not found";
        boolean status = false;
        for (String key : KEYS) {
            String[]pub=key.split(" ");
            if (pub[2].equals(identity)) {
                message = "public key found";
                status = true;
                try {
                    KeyFactory rsaFactory = KeyFactory.getInstance("RSA");
                    Decoder decoder = Base64.getDecoder();
                    System.out.println(pub[1]);
                    X509EncodedKeySpec pubKeySpec = new X509EncodedKeySpec(decoder.decode(pub[1]));
                    PublicKey pubKey = rsaFactory.generatePublic(pubKeySpec);
                    doc.append("AES128", publicEncrypt(pubKey));
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

    private String publicEncrypt(PublicKey publicKey) {
        Cipher cipher;
        String encoded = null;
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES/CBC/PKCS5PADDING");
            generator.init(128);
            SecretKey secretKey = generator.generateKey();
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

    private class Connection extends Thread {
        BufferedReader in;
        BufferedWriter out;
        protected Socket socket;
        private boolean connected;

        public Connection(Socket socket) {
            this.socket = socket;

        }

        public void run() {
            try {
                connected = true;
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                while (connected) {
                    String msg = in.readLine();
                    Document doc = Document.parse(msg);
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}