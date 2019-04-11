package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
    private static Logger log = Logger.getLogger(ServerMain.class.getName());
    private static final int PORT = Integer
            .parseInt(Configuration.getConfigurationValue("port"));
    private static final String[] PEERS = Configuration
            .getConfigurationValue("peers").split(",");

    protected FileSystemManager fileSystemManager;
    private Client client;
    private Server server;

    public ServerMain() throws NumberFormatException, IOException,
            NoSuchAlgorithmException {
        fileSystemManager = new FileSystemManager(
                Configuration.getConfigurationValue("path"), this);
        server = new Server(PORT, this.fileSystemManager);
        client = new Client(PEERS[0],fileSystemManager);
    }

    @Override
    public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
        Document message = new Document();
        message.append("command", fileSystemEvent.event.toString());
        if(fileSystemEvent.fileDescriptor!=null)
        message.append("fileDescriptor",
                fileSystemEvent.fileDescriptor.toDoc());
        message.append("pathName", fileSystemEvent.pathName);
        try {
            if (client!=null&&client.connected)
                client.sendToServer(message.toJson());
            server.sendToClients(message.toJson());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    public void handleMsg() {
//        System.out.println("fsdd");
//        while (true) {
//            if (client.connected)
//            messages.add(client.incomingMsg());
//            messages.addAll(server.incomingMsg());
//            for (String message : messages) {
//                if(message!=null)
//                System.out.println(message);
//            }
//            messages.clear();
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//        switch (msg) {
//
//        }
//    }
}
