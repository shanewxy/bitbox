package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

import javax.print.Doc;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.EVENT;
import unimelb.bitbox.util.FileSystemManager.FileDescriptor;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class MessageHandler {

    private static final Long BLOCKSIZE = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
    private static Logger log = Logger.getLogger(MessageHandler.class.getName());

    FileSystemManager fileSystemManager;

    public MessageHandler(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;

    }

    /**
     * handle incoming message
     */
    public ArrayList<Document> handleMsg(String msg) {
        System.out.println(msg);
        Document json = (Document) Document.parse(msg);
        String pathName = json.getString("pathName");
        String command = json.getString("command");
        long lastModified = 0;
        String md5 = null;
        if (json.containsKey("fileDescriptor")) {
            Document fileDescriptor = (Document) json.get("fileDescriptor");
            lastModified = fileDescriptor.getLong("lastModified");
            md5 = fileDescriptor.getString("md5");
        }

        ArrayList<Document> responses = new ArrayList<Document>();

        boolean result = false;

        switch (command) {
        case "FILE_DELETE_REQUEST":
            responses.add(handleFileDeleteRequest(json));
            break;

        case "DIRECTORY_CREATE_REQUEST":
            Document DirCResp = new Document();
            DirCResp.append("command", "DIRECTORY_CREATE_RESPONSE");
            DirCResp.append("pathName", pathName);
            try {
                if (!fileSystemManager.isSafePathName(pathName)) {
                    DirCResp.append("message", "unsafe pathname given");
                    DirCResp.append("status", false);
                } else {
                    if (fileSystemManager.dirNameExists(pathName)) {
                        DirCResp.append("message", "pathname already exists");
                        DirCResp.append("status", false);
                    } else {
                        if (fileSystemManager.makeDirectory(pathName)) {
                            DirCResp.append("message", "directory created");
                            DirCResp.append("status", true);
                        }

                    }
                }
            } catch (Exception e) {
                DirCResp.append("message", "there was a problem creating the directory");
                DirCResp.append("status", false);
            }
            responses.add(DirCResp);
            break;
        case "DIRECTORY_DELETE_REQUEST":
            Document DirDResp = new Document();
            DirDResp.append("command", "DIRECTORY_DELETE_RESPONSE");
            DirDResp.append("pathName", pathName);
            try {
                if (!fileSystemManager.isSafePathName(pathName)) {
                    DirDResp.append("message", "unsafe pathname given");
                    DirDResp.append("status", false);
                } else {
                    if (!fileSystemManager.dirNameExists(pathName)) {
                        DirDResp.append("message", "pathname does not exists");
                        DirDResp.append("status", false);
                    } else {
                        if (fileSystemManager.deleteDirectory(pathName)) {
                            DirDResp.append("message", "directory deleted");
                            DirDResp.append("status", true);
                        }
                    }
                }
            } catch (Exception e) {
                DirDResp.append("message", "there was a problem deleting the directory");
                DirDResp.append("status", false);
            }
            responses.add(DirDResp);
            break;
        case "DIRECTORY_CREATE_RESPONSE":
            log.info("message :" + json.getString("message") + " status : " + json.getBoolean("status"));
            break;
        case "DIRECTORY_DELETE_RESPONSE":
            log.info("message :" + json.getString("message") + " status : " + json.getBoolean("status"));
            break;

        case "FILE_CREATE_REQUEST":
            responses = handleFileCreateRequest(json);
            break;

        case "FILE_CREATE_RESPONSE":
            log.info("message :" + json.getString("message") + " status : " + json.getBoolean("status"));
            break;

        case "FILE_BYTES_REQUEST":
            responses = handleFileBytesRequest(json);
            break;

        case "FILE_BYTES_RESPONSE":
            responses = handleFileBytesResponse(json);
            break;
        }

        return responses;
    }

    /**
     * handle file delete request
     * 
     * @param json
     * @return response
     */
    private Document handleFileDeleteRequest(Document json) {
        String message = "";
        boolean status = false;
        String pathName = json.getString("pathName");
        Document fileDescriptor = (Document) json.get("fileDescriptor");
        long lastModified = fileDescriptor.getLong("lastModified");
        String md5 = fileDescriptor.getString("md5");
        if (!fileSystemManager.isSafePathName(pathName)) {
            message = "unsafe pathname given";
        } else if (!fileSystemManager.fileNameExists(pathName)) {
            message = "pathname does not exist";
        } else {
            try {
                status = fileSystemManager.deleteFile(pathName, lastModified, md5);
                message = "file successfully deleted";
            } catch (Exception e) {
                message = "there was a problem deleting the file";
            }
        }
        appendResponseInfo(json, Command.FILE_DELETE_RESPONSE, message, status);
        return json;
    }

    /**
     * parse event to Json
     */
    public String toJson(FileSystemEvent fileSystemEvent) {
        Document message = new Document();
        message.append("command", fileSystemEvent.event + "_REQUEST");
        if (fileSystemEvent.fileDescriptor != null)
            message.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
        message.append("pathName", fileSystemEvent.pathName);
        return message.toJson();
    }

    /**
     * append general info when creating response protocol
     * 
     * @param json
     * @param cmd
     * @param message
     * @param status
     */
    public void appendResponseInfo(Document json, Command cmd, String message, boolean status) {
        json.replace("command", cmd.toString());
        json.append("message", message);
        json.append("status", status);
    }

    /**
     * handle incoming File_Create_Request
     * 
     * @param msg
     * @return
     */
    public ArrayList<Document> handleFileCreateRequest(Document json) {
        ArrayList<Document> responses = new ArrayList<Document>();

        String message = "";
        boolean result = false;

        String pathName = json.getString("pathName");
        Document fileDescriptor = (Document) json.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long fileSize = fileDescriptor.getLong("fileSize");
        long lastModified = fileDescriptor.getLong("lastModified");

        if (!fileSystemManager.isSafePathName(pathName)) {
            message = "unsafe pathname given";
        } else if (fileSystemManager.fileNameExists(pathName)) {
            message = "pathname already exists";
        } else {
            try {
                if (fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {

                    result = true;

                    if (fileSystemManager.checkShortcut(pathName)) {
                        // use a local copy
                        message = "use a local copy to create the file";

                    } else {
                        Document json1 = Document.parse(json.toJson());
                        message = "file loader ready";
                        appendResponseInfo(json1, Command.FILE_CREATE_RESPONSE, message, result);

                        Document json2 = Document.parse(json.toJson());
                        json2.replace("command", "FILE_BYTES_REQUEST");
                        json2.append("position", 0);
                        if (fileSize < BLOCKSIZE) {
                            json2.append("length", fileSize);
                        } else {

                            json2.append("length", BLOCKSIZE);
                        }

                        responses.add(json1);
                        responses.add(json2);

                        return responses;
                    }
                } else {
                    message = "file loader already exists";
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("file loader already exists");
            }

        }

        appendResponseInfo(json, Command.FILE_CREATE_RESPONSE, message, result);
        responses.add(json);
        return responses;
    }

    /**
     * handle file_bytes_request
     * 
     * @param msg
     * @return
     */
    private ArrayList<Document> handleFileBytesRequest(Document json) {
        ArrayList<Document> responses = new ArrayList<Document>();
        String message = "";
        boolean result = false;

        Document fileDescriptor = (Document) json.get("fileDescriptor");
        String md5 = fileDescriptor.getString("md5");
        long fileSize = fileDescriptor.getLong("fileSize");
        long position = json.getLong("position");

        try {
            ByteBuffer buffer = fileSystemManager.readFile(md5, position, (fileSize - position) > BLOCKSIZE ? BLOCKSIZE : (fileSize - position));
            if (buffer != null) {
                Base64.Encoder encoder = Base64.getEncoder(); // get encoder
                buffer.flip();
                byte[] bytes = new byte[buffer.limit()];
                buffer.get(bytes);

                json.append("content", encoder.encodeToString(bytes));
                message = "successful read";
                result = true;

            } else {
                message = "unsuccessful read, failed to read from source file";
            }
        } catch (NoSuchAlgorithmException e) {
            message = e.getMessage();
        } catch (IOException e) {
            message = e.getMessage();
        }
        appendResponseInfo(json, Command.FILE_BYTES_RESPONSE, message, result);
        responses.add(json);
        return responses;
    }

    /**
     * handle file_bytes_response
     * 
     * @param msg
     * @return
     */
    private ArrayList<Document> handleFileBytesResponse(Document json) {
        ArrayList<Document> responses = new ArrayList<Document>();
        String content = json.getString("content");
        long position = json.getLong("position");
        String pathName = json.getString("pathName");

        Document fileDescriptor = (Document) json.get("fileDescriptor");
        long fileSize = fileDescriptor.getLong("fileSize");

        Base64.Decoder decoder = Base64.getDecoder(); // get decoder
        byte[] bytes = decoder.decode(content);
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

        buffer.put(bytes, 0, bytes.length);
        buffer.flip();

        try {
            if (fileSystemManager.writeFile(pathName, buffer, position)) {
                if (fileSystemManager.checkWriteComplete(pathName)) {
                    log.info("file :" + pathName + "successfully transfered");
                } else {

                    json.replace("command", "FILE_BYTES_REQUEST");
                    json.remove("content");
                    json.remove("message");
                    json.remove("status");

                    if (fileSize - position < BLOCKSIZE) {
                        json.replace("position", fileSize - position);
                    } else {
                        json.replace("position", json.getLong("position") + buffer.capacity());
                    }
                    responses.add(json);
                }

            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return responses;
    }
}
