package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.HostPort;

/**
 * The component aims to manage incoming remote peers connections,
 * the main communication procedure locates in the connection class {@link #connection(Socket, MessageHandler)}
 * @author Xueying Wang
 * @author Yichen Liu
 */
public class PeerServer {
	
	/**
	 * Using AtomicInteger to make a thread safe counter for connected peers
	 */
    public static AtomicInteger clientCount;
    private static Logger log = Logger.getLogger(PeerServer.class.getName());
    private ServerSocket ss;

    /* Used a normal HashMap rather than ConcurrentHashMap because each connection object (the key in the map)
     * is distinct so it will not cause infinite-recursion when multiple threads manipulate the same HashMap
     */
    public static HashMap<Connection, HostPort> connections = new HashMap<Connection, HostPort>();
    
	public static int maximumConnections = Integer.parseInt(Configuration.getConfigurationValue("maximumIncommingConnections"));
	
	public static HostPort localHostPort = new HostPort(Configuration.getConfigurationValue("advertisedName"), Integer.parseInt(Configuration.getConfigurationValue("port")));
	
	public PeerServer(int port, MessageHandler handler) {
        try {
            ss = new ServerSocket(port);
            clientCount = new AtomicInteger();
        } catch (IOException e) {
            log.warning(e.getMessage());
        }
        // Create a thread for waiting incoming connections so the server will not block the main process in ServerMain
        Runnable listener = () -> {
            try {
                while (true) {
                    Socket socket = ss.accept();
                    Connection conn = new Connection(socket, handler);
                    conn.start();
                }
            } catch (IOException e) {
                log.warning(e.getMessage());
            }
        };
        new Thread(listener).start();
    }

    public void sendToClients(String msg) {
    	// Make sure the disconnection of some clients will not interfere broadcasting messages
    	synchronized(connections) {
    		if(connections.isEmpty()) {
//    			log.warning("This peer currently does not have any client");
    			return;
    		}
            for (Connection connection : connections.keySet()) {
                try {
                    synchronized(connection.out) {
                        connection.out.write(msg+System.lineSeparator());
                        connection.out.flush();
                        
                    }
                } catch(SocketException e1) {
                    log.warning(e1.getMessage()+"hi aaron");
                }
                catch (IOException e) {
                    log.warning(e.getMessage());
                }
            }
    	}
    }

}