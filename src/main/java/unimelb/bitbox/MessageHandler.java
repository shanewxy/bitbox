package unimelb.bitbox;

import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class MessageHandler {
    String cmd = null;
    String pathName = null;
    Long lastModified = null;
    String md5 = null;
    Long fileSize = null;
    FileSystemManager fileSystemManager;

    public MessageHandler(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;

    }

    /**
     * handle incoming message
     */
    public boolean handleMsg(String msg) {
        System.out.println(msg);
        Document json = Document.parse(msg);
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
                fileSize = fileDescriptor.getLong("fileSize");
            }
        }
        boolean result = false;
        switch (cmd) {
        case "FILE_DELETE_REQUEST":
            if (fileSystemManager.fileNameExists(pathName))
                result = fileSystemManager.deleteFile(pathName, lastModified,
                        md5);
            break;
        case "DIRECTORY_CREATE_REQUEST":
            result = fileSystemManager.makeDirectory(pathName);
            break;
        }
        return result;

    }

    /**
     * parse event to Json
     */
    public String toJson(FileSystemEvent fileSystemEvent) {
        Document message = new Document();
        message.append("command", fileSystemEvent.event + "_REQUEST");
        if (fileSystemEvent.fileDescriptor != null)
            message.append("fileDescriptor",
                    fileSystemEvent.fileDescriptor.toDoc());
        message.append("pathName", fileSystemEvent.pathName);
        return message.toJson();
    }
    /**
     * marshall the result and event into Json
     */
}
