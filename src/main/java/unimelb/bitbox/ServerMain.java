package unimelb.bitbox;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int PORT = Integer.parseInt(Configuration.getConfigurationValue("port"));
    public static final String[] PEERS = Configuration.getConfigurationValue("peers").split(",");
    private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
    private static final String PATH = Configuration.getConfigurationValue("path");
    private static final String MODE = Configuration.getConfigurationValue("mode");
    private static final int UDPPORT = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
    public static final int UDPTIMEOUT = Integer.parseInt(Configuration.getConfigurationValue("udpTimeOut"));
    public static final int UDPATTEMPTS = Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));
    private List<File> list;

    protected FileSystemManager fileSystemManager;
    private PeerClient peerClient;
    private PeerServer peerServer;
    private MessageHandler handler;
    private UDPServer udpServer;
    private UDPClient udpClient;
    private UDPAgent a;

    public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
    	
    	fileSystemManager = new FileSystemManager(PATH, this);
    	
    	// delete existing fileLoader
    	list = new ArrayList<File>();  
    	cancelExistFileLoader(PATH);
        
        handler = new MessageHandler(fileSystemManager);
        
        if (MODE.equals("tcp")) {
        	peerServer = new PeerServer(PORT, handler);
        	for(String peer : PEERS) {
        		peerClient = new PeerClient(peer, handler);
        		if(peerClient.connected) {
        			break;
        		}
        	}
		}else if (MODE.equals("udp")) {
//			udpServer = new UDPServer(UDPPORT, handler);
//			for (String peer : PEERS) {
//				udpClient = new UDPClient(peer, handler);
//				if (udpClient.connected) {
//					break;
//				}
//			}
			a = new UDPAgent(UDPPORT, handler, PEERS);
		}
        
    }

    /**
     * delete exist FileLoader if there is any
     * @param path
     */
    public void cancelExistFileLoader(String path) {
    	readAllFile(PATH);
		for (File file : list) {
			String fileName = file.getName();
			if (fileName.endsWith("(bitbox)")) {
				file.delete();
			}
		}
	}
    
    /**
     * read all files under the given directory
     * @param filePath
     */
    public void readAllFile(String filePath) {  
        File f = new File(filePath);  
        File[] files = f.listFiles();  
        for (File file : files) {  
            if(file.isDirectory()) {  
                readAllFile(file.getPath());  
            } else {  
                this.list.add(file);  
            }  
        }  
    }  

	@Override
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        String msg = handler.toJson(fileSystemEvent);
        try {
        	if (MODE.equals("tcp")) {
        		if (peerClient != null && peerClient.connected)
        			peerClient.sendToServer(msg);
        		peerServer.sendToClients(msg);
			}else if(MODE.equals("udp")){
				a.sendToPeers(msg);
//				udpServer.sendToClients(msg);
//				if (udpClient != null && udpClient.connected) {
//					udpClient.sendToServer(msg);
//				}
				
			}
        	
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
  

}
