package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	
	//Local ip address& port number
	private String ipAddr;
	private int portNum;
	
	private HashMap<HostPort, PeerClient> clients;
	
	private PeerServer localServer;
	
	/**
	 * Try to establish connection to every peer listed in the configuration file,
	 * every successfully created PeerClient will be stored into a HashMap with its
	 * "ipAddress:portNumber" as key.
	 */
	private void joinInitialPeersGroup() {
		String[] peers = Configuration.getConfigurationValue("peers").split(",");
		
		for(String peer : peers) {
			HostPort hp = new HostPort(peer);
			String host = hp.host;
			int port = hp.port;
			if(port != portNum || !(host.equals(ipAddr))) {
				PeerClient newClient = new PeerClient(host, port);
				if(newClient.initConnection()) {
					clients.put(hp, newClient);
					//Create a new thread for each successfully created peer client to handle their affairs
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					new Thread(() -> newClient.processMsg()).start();
				}
			}
		}
	}
	
	public void test() {
		localServer = new PeerServer(portNum, fileSystemManager);
	}
	
	public ServerMain(int portNum) throws NumberFormatException, IOException, NoSuchAlgorithmException {
		this.portNum = portNum;
		this.ipAddr = Configuration.getConfigurationValue("advertisedName");
		clients = new HashMap<HostPort, PeerClient>();
		
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		
		//Try to connect every peer listed in the configuration file
		joinInitialPeersGroup();
//		Thread t = new Thread(() -> test());
//		t.start();
//		PeerClient newClient = new PeerClient("43.240.97.106", 3000);
//		newClient.initConnection();
		
		//Establish a server for the local peer
		localServer = new PeerServer(portNum, fileSystemManager);
		
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
//		System.out.println(fileSystemEvent.toString() + fileSystemEvent.fileDescriptor.lastModified);
		// TODO: process events
	}
	
}
