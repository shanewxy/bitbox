package unimelb.bitbox;
/**
* @author: Xueying Wang
*/

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class Client {
    DataInputStream in;
    DataOutputStream out;

    public void connectToPeer(String peer) {
        Socket s = null;
        String address = peer.split(":")[0];
        int port = Integer.parseInt(peer.split(":")[1]);
        try {
            s = new Socket(address, port);
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
//            out.writeUTF("hello world"); // UTF is a string encoding;
            String data = in.readUTF();
            System.out.println("Received: " + data);
        } catch (UnknownHostException e) {
            System.out.println("Sock:" + e.getMessage());
        } catch (EOFException e) {
            System.out.println("EOF:" + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        } finally {
            if (s != null)
                try {
                    s.close();
                } catch (IOException e) {
                    /* close failed */}
        }
    }
    /**
     * send message to server.
     */
    public void sendToServer(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}