package unimelb.bitbox;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class Server {
	private ServerSocket serverSocket;
	private List<Connection> connections = new ArrayList<Connection>();
	private static int counter = 0;
	private static Logger log = Logger.getLogger(Server.class.getName());

	public Server(int port) {
		System.out.println("server listening for a connection");
		try {
			serverSocket = new ServerSocket(port);

		} catch (IOException e) {
			System.out.println(e.getMessage());
		}

		Runnable listener = () -> {
			try {
				while (true) {
					Socket socket = serverSocket.accept();
					Connection conn = new Connection(socket);
					conn.start();
					connections.add(conn);
				}
			} catch (IOException e) {
				System.err.println(e);
			}
		};
		new Thread(listener).start();

	}

	public void sendToClients(String string) {
		for (Connection c : connections) {
			if (c != null) {
				try {
					c.out.writeUTF(string);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}

}
