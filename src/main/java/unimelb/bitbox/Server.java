package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import unimelb.bitbox.util.Configuration;

/**
 * @author : Xueying Wang
 */
public class Server {
    ServerSocket securitySocket;
    BufferedReader in;
    BufferedWriter out;
    private static final int clientPort = Integer.parseInt(Configuration.getConfigurationValue("clientPort"));

    /**
     * 
     */
    public ServerSocket ss;

    /**
     * 
     */
    public Server() {
    }

    public void initClientPort() {
        try {
            securitySocket = new ServerSocket(clientPort);
            boolean connected = false;
            while (!connected) {
                Socket socket = securitySocket.accept();
                connected = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}