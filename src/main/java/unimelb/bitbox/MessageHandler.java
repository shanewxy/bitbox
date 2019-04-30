package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

/**
 * Contains methods to handle incoming messages for file system events. Pass
 * received json to {@link #handleMsg(String)}, process the event and return
 * result to the event sender.
 *
 */
public class MessageHandler {
    private enum Command {
        INVALID_PROTOCOL, CONNECTION_REFUSED, HANDSHAKE_REQUEST, HANDSHAKE_RESPONSE, FILE_CREATE_REQUEST, FILE_CREATE_RESPONSE, FILE_DELETE_REQUEST, FILE_DELETE_RESPONSE, FILE_MODIFY_REQUEST,
        FILE_MODIFY_RESPONSE, DIRECTORY_CREATE_REQUEST, DIRECTORY_CREATE_RESPONSE, DIRECTORY_DELETE_REQUEST, DIRECTORY_DELETE_RESPONSE, FILE_BYTES_REQUEST, FILE_BYTES_RESPONSE
    }

    private static final Long BLOCKSIZE = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
    private static Logger log = Logger.getLogger(MessageHandler.class.getName());

    FileSystemManager fileSystemManager;

    public MessageHandler(FileSystemManager fileSystemManager) {
        this.fileSystemManager = fileSystemManager;

    }

    /**
     * handle incoming messages, unmarshall json string and call different methods
     * according to command
     * 
     * @param msg
     * @return response and bytes request when modify, response only for other
     *         command
     */
    public List<Document> handleMsg(String msg) {
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

        List<Document> responses = new ArrayList<Document>();

        boolean result = false;

        switch (command) {
        case "FILE_DELETE_REQUEST":
            responses.add(handleFileDeleteRequest(json));
            break;
        case "FILE_MODIFY_REQUEST":
            responses = handleFileModifyRequest(json);
            break;
        case "DIRECTORY_CREATE_REQUEST":
            responses = handleDirCreateRequest(json);
            break;
        case "DIRECTORY_DELETE_REQUEST":
            responses = handleDirDeleteRequest(json);
            break;

        case "FILE_CREATE_REQUEST":
            responses = handleFileCreateRequest(json);
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
            if (fileSystemManager.deleteFile(pathName, lastModified, md5)) {
                status = true;
                message = "file successfully deleted";
            } else
                message = "there was a problem deleting the file";
        }

        appendResponseInfo(json, Command.FILE_DELETE_RESPONSE, message, status);
        return json;
    }

    /**
     * handle file modify request
     * 
     * @param json
     * @return file modify response and file bytes request
     */
    private List<Document> handleFileModifyRequest(Document json) {
        List<Document> responses = new ArrayList();
        String message = "";
        boolean status = false;
        String pathName = json.getString("pathName");
        Document fileDescriptor = (Document) json.get("fileDescriptor");
        long lastModified = fileDescriptor.getLong("lastModified");
        long fileSize = fileDescriptor.getLong("fileSize");
        String md5 = fileDescriptor.getString("md5");
        Document json1 = Document.parse(json.toJson());
        if (!fileSystemManager.isSafePathName(pathName)) {
            message = "unsafe pathname given";
        } else if (!fileSystemManager.fileNameExists(pathName)) {
            message = "pathname does not exist";
        } else if (fileSystemManager.fileNameExists(pathName, md5)) {
            message = "pathname already exists with matching content";
        } else {
            try {
                fileSystemManager.cancelFileLoader(pathName);
                if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
                    status = true;
                    if (fileSystemManager.checkShortcut(pathName)) {
                        // use a local copy
                        message = "use a local copy to create the file";

                    } else {
                        message = "file loader ready";

                        Document json2 = Document.parse(json.toJson());
                        json2.replace("command", "FILE_BYTES_REQUEST");
                        json2.append("position", 0);
                        if (fileSize < BLOCKSIZE) {
                            json2.append("length", fileSize);
                        } else {
                            json2.append("length", BLOCKSIZE);
                        }

                        responses.add(json2);

                    }
                } else {
                    message = "there was a problem modifying the file";
                }

            } catch (IOException e) {
                message = "file loader already exists";

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }
        appendResponseInfo(json1, Command.FILE_MODIFY_RESPONSE, message, status);
        responses.add(json1);
        return responses;
    }

    // handle DIRECTORY_CREATE_RESPONSE

    private List<Document> handleDirCreateRequest(Document json) {
        List<Document> responses = new ArrayList();
        String pathName = json.getString("pathName");
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
        return responses;
    }

    // handle DIRECTORY_DELETE_RESPONSE

    private List<Document> handleDirDeleteRequest(Document json) {
        List<Document> responses = new ArrayList();
        String pathName = json.getString("pathName");
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
        return responses;
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
        Document json1 = Document.parse(json.toJson());

        if (!fileSystemManager.isSafePathName(pathName)) {
            message = "unsafe pathname given";
        } else if (fileSystemManager.fileNameExists(pathName)) {
            message = "pathname already exists";
        } else {
            try {
                fileSystemManager.cancelFileLoader(pathName);
                if (fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {

                    result = true;

                    if (fileSystemManager.checkShortcut(pathName)) {
                        // use a local copy
                        message = "use a local copy to create the file";
                    } else {
                        message = "file loader ready";
                        Document json2 = Document.parse(json.toJson());
                        json2.replace("command", "FILE_BYTES_REQUEST");
                        json2.append("position", 0);
                        if (fileSize < BLOCKSIZE) {
                            json2.append("length", fileSize);
                        } else {

                            json2.append("length", BLOCKSIZE);
                        }

                        responses.add(json2);
                        return responses;
                    }
                } else {
                    message = "there was a problem creating the file";
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (IOException e) {
                message = "file loader already exists";
            }

        }
        appendResponseInfo(json1, Command.FILE_CREATE_RESPONSE, message, result);
        responses.add(json1);
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

    /**
     * parse event to Json
     * 
     * @param fileSystemEvent
     * @return json string of the event
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
}
