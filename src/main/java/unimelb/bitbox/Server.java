package unimelb.bitbox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.FileSystemManager;

public class Server {

    public volatile static long clientCount = 0;
    private static Logger log = Logger.getLogger(Server.class.getName());
    private ServerSocket sock;

    public List<Connection> connections = new ArrayList<Connection>();

    public Server(int port, MessageHandler handler) {
        try {
            sock = new ServerSocket(port);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        Runnable listener = () -> {
            try {
                while (true) {
                    Socket socket = sock.accept();
                    Connection conn = new Connection(socket, handler);
                    conn.start();
                    connections.add(conn);
                }
            } catch (IOException e) {
                System.err.println(e);
            }
        };
        new Thread(listener).start();
    }

    public void sendToClients(String msg) {
        for (Connection connection : connections) {
            try {
            	if (connection != null) {
					connection.out.write(msg + "\n");
					connection.out.flush();
				}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}