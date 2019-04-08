package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	
	//Local ip address& port number
	private String ipAddr;
	private int portNum;
	
	private HashMap<String, PeerClient> clients;
	
	private PeerServer localServer;
	
	/**
	 * Try to establish connection to every peer listed in the configuration file,
	 * every successfully created PeerClient will be stored into a HashMap with its
	 * "ipAddress:portNumber" as key.
	 */
	private void joinPeersGroup() {
		String[] peers = Configuration.getConfigurationValue("peers").split(",");
		
		for(String peer : peers) {
			// For each peer's detail info: [ipAddress, portNumber]
			String[] peerDetail = peer.split(":");
			String ip = peerDetail[0];
			int pn = Integer.parseInt(peerDetail[1]);
			if(pn != portNum || !(ip.equals(ipAddr))) {
				PeerClient newClient = new PeerClient(ip, pn);
				if(newClient != null) {
					clients.put(peer, newClient);
				}
			}
		}
	}
	
	public ServerMain(int portNum) throws NumberFormatException, IOException, NoSuchAlgorithmException {
		this.portNum = portNum;
		this.ipAddr = Configuration.getConfigurationValue("advertisedName");
		clients = new HashMap<String, PeerClient>();
		
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		
		//Try to connect every peer listed in the configuration file
		joinPeersGroup();
		
		//Establish a server for the local peer
		localServer = new PeerServer(portNum);
		
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		// TODO: process events
	}
	
}
