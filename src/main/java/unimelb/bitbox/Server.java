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
    private static Logger log = Logger
            .getLogger(FileSystemManager.class.getName());
    private ServerSocket sock;
    
    public List<Connection> connections = new ArrayList<Connection>();
//
//    public List<Connection> getConnections() {
//        return connections;
//    }

    public Server(int port,FileSystemManager fileSystemManager) {
        try {
            sock = new ServerSocket(port);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        Runnable listener = () -> {
            try {
                while (true) {
                    Socket socket = sock.accept();
                    Connection conn = new Connection(socket,fileSystemManager);
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
                connection.out.writeUTF(msg);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
//
//    public List<String> incomingMsg() {
//        List<String> msg = new ArrayList<String>();
////        System.out.println(msg);
//        for (Connection connection : connections) {
//            String message=null;
//            try {
//                message = connection.in.readUTF();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            if(msg!=null)
//            msg.add(message);
//        }
//        return msg;
//    }

}