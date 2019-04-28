package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int PORT = Integer.parseInt(Configuration.getConfigurationValue("port"));
    private static final String[] PEERS = Configuration.getConfigurationValue("peers").split(",");
    private static final int SYNCINTERVAL = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));

    protected FileSystemManager fileSystemManager;
    private Client client;
    private Server server;
    private MessageHandler handler;

    public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
        fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
        handler = new MessageHandler(fileSystemManager);
        server = new Server(PORT, handler);
        for(String peer : PEERS) {
            client = new Client(peer, handler);
            if(client.connected) {
            	break;
            }
        }
        new Thread(() -> broadcastSyncEvent()).start();
    }

    @Override
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        String msg = handler.toJson(fileSystemEvent);
        try {
            if (client != null && client.connected)
                client.sendToServer(msg);
            server.sendToClients(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * After each sync interval, broadcast local share directory's content to other connected peers
     */
    private void broadcastSyncEvent() {
    	while(true) {
    		try {
				Thread.sleep(1000*SYNCINTERVAL);
				if(server == null) {
					continue;
				}
    			log.info("Synchronizing...");
				for(FileSystemEvent event : fileSystemManager.generateSyncEvents()) {
					processFileSystemEvent(event);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
    	}
    }

}
