package unimelb.bitbox;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

/**
 * @author: Xueying Wang
 */
public class Connection extends Thread {
    DataInputStream in;
    DataOutputStream out;
    protected Socket socket;
    private FileSystemManager fileSystemManager;

    public Connection(Socket socket, FileSystemManager fileSystemManager) {
        this.socket = socket;
        this.in = null;
        this.out = null;
        this.fileSystemManager = fileSystemManager;
    }

    public void run() {
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            while (true) {
                String msg = in.readUTF();
                handleMsg(msg);
            }
        } catch (IOException e) {
            return;
        }

    }

    public boolean handleMsg(String msg) {
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

}