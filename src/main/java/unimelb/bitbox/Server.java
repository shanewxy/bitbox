package unimelb.bitbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;

public class Server {

    public static AtomicInteger clientCount;
    private static Logger log = Logger.getLogger(FileSystemManager.class.getName());
    private ServerSocket sock;

    public static HashMap<Connection, HostPort> connections = new HashMap<Connection, HostPort>();
    
	public static int maximumConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	public static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));
	
	public Server(int port, MessageHandler handler) {
        try {
            sock = new ServerSocket(port);
            clientCount = new AtomicInteger();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Runnable listener = () -> {
            try {
                while (true) {
                    Socket socket = sock.accept();
                    Connection conn = new Connection(socket, handler);
                    conn.start();
//                    connections.add(conn);
                }
            } catch (IOException e) {
                System.err.println(e);
            }
        };
        new Thread(listener).start();
    }

    public void sendToClients(String msg) {
        for (Connection connection : connections.keySet()) {
            try {
                connection.out.write(msg+System.lineSeparator());
                connection.out.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}