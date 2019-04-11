package unimelb.bitbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
/**
* @author: Xueying Wang
*/

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

public class Client implements Runnable {
    DataInputStream in;
    DataOutputStream out;
    public boolean connected = false;
    private FileSystemManager fileSystemManager;

    public Client(String peer, FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;
        Socket s = null;
        String address = peer.split(":")[0];
        int port = Integer.parseInt(peer.split(":")[1]);
        try {
            s = new Socket(address, port);
            in = new DataInputStream(s.getInputStream());
            out = new DataOutputStream(s.getOutputStream());
            System.out.println("connected to server " + address);
            connected = true;
            new Thread(this).start();
        } catch (UnknownHostException e) {
            System.out.println("Sock:" + e.getMessage());
        } catch (EOFException e) {
            System.out.println("EOF:" + e.getMessage());
        } catch (IOException e) {
            System.out.println("IO:" + e.getMessage());
        }
    }

    /**
     * send message to server.
     */
    public void sendToServer(String msg) {
        try {
            out.writeUTF(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean incomingMsg(String msg) {
        System.out.println(msg);
        Document json = Document.parse(msg);
        String cmd = null;
        String pathName = null;
        Long lastModified = null;
        String md5 = null;
        if (json.containsKey("command")) {
            cmd = json.getString("command");
        }
        if (json.containsKey("pathName")) {
            pathName = json.getString("pathName");
        }
        if (json.containsKey("fileDescriptor")) {
            Document fileDescriptor = (Document) json.get("fileDescriptor");
            if (fileDescriptor.containsKey("lastModified")) {
                lastModified = fileDescriptor.getLong("lastModified");
            }
            if (json.containsKey("md5")) {
                md5 = fileDescriptor.getString("md5");
            }
            if (json.containsKey("filezSize")) {
                Long fileSize = fileDescriptor.getLong("fileSize");
            }
        }
        boolean result = false;
        switch (cmd) {
        case "FILE_DELETE":
            if (fileSystemManager.fileNameExists(pathName))
                result = fileSystemManager.deleteFile(pathName, lastModified,
                        md5);
            break;
        }
        return result;
    }

    @Override
    public void run() {
        while (true) {
            String data = null;
            try {
                data = in.readUTF();
                if (data != null) {
                    incomingMsg(data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
