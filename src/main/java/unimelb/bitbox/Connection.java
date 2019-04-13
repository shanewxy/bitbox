package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;

public class Connection extends Thread {
    BufferedReader in;
    BufferedWriter out;
    protected Socket socket;
    private MessageHandler handler;

    public Connection(Socket socket, MessageHandler handler) {
        this.socket = socket;
        this.in = null;
        this.out = null;
        this.handler = handler;
    }

    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            while (true) {
                String msg = in.readLine();
                handler.handleMsg(msg);
            }
        } catch (IOException e) {
            return;
        }

    }

//    public boolean handleMsg(String msg) {
//        System.out.println(msg);
//        Document json = Document.parse(msg);
//        String cmd = null;
//        String pathName = null;
//        Long lastModified = null;
//        String md5 = null;
//        if (json.containsKey("command")) {
//            cmd = json.getString("command");
//        }
//        if (json.containsKey("pathName")) {
//            pathName = json.getString("pathName");
//        }
//        if (json.containsKey("fileDescriptor")) {
//            Document fileDescriptor = (Document) json.get("fileDescriptor");
//            if (fileDescriptor.containsKey("lastModified")) {
//                lastModified = fileDescriptor.getLong("lastModified");
//            }
//            if (json.containsKey("md5")) {
//                md5 = fileDescriptor.getString("md5");
//            }
//            if (json.containsKey("filezSize")) {
//                Long fileSize = fileDescriptor.getLong("fileSize");
//            }
//        }
//        boolean result = false;
//        switch (cmd) {
//        case "FILE_DELETE_REQUEST":
//            if (fileSystemManager.fileNameExists(pathName))
//                result = fileSystemManager.deleteFile(pathName, lastModified,
//                        md5);
//            break;
//        case "DIRECTORY_CREATE_REQUEST":
//            result = fileSystemManager.makeDirectory(pathName);
//            break;
//
//            
//        }
//            
//        return result;
//    }

}