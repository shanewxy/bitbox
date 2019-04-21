package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.logging.Logger;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class MessageHandler {

	private static final Long BLOCKSIZE = Long.parseLong(Configuration.getConfigurationValue("blockSize"));
	private static Logger log = Logger.getLogger(MessageHandler.class.getName());

	FileSystemManager fileSystemManager;
	// String cmd = null;
	// String pathName = null;
	// long lastModified;
	// String md5 = null;
	// long fileSize;
	// boolean status;
	// String message;
	// String content;
	// long length;
	// long position;

	public MessageHandler(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;

	}

	/**
	 * handle incoming message
	 */
	public ArrayList<Document> handleMsg(String msg) {
		System.out.println(msg);
		Document json = (Document) Document.parse(msg);
		// parseJsonMsg(msg);
		String command = json.getString("command");
		ArrayList<Document> responses = new ArrayList<Document>();
		boolean result = false;
		// String returnMsg = "";

		switch (command) {
		// case "FILE_DELETE_REQUEST":
		// if (fileSystemManager.fileNameExists(pathName))
		// result = fileSystemManager.deleteFile(pathName, lastModified, md5);
		// break;
		//
		// case "DIRECTORY_CREATE_REQUEST":
		// result = fileSystemManager.makeDirectory(pathName);
		// break;
		//
		// case "DIRECTORY_DELETE_REQUEST":
		// result = fileSystemManager.deleteDirectory(pathName);
		// break;

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
	 * unmarshall message from received Json and store info into variables
	 * 
	 * @param msg
	 */
	// public void parseJsonMsg(String msg) {
	// Document json = Document.parse(msg);
	// if (json.containsKey("command")) {
	// cmd = json.getString("command");
	// }
	// if (json.containsKey("pathName")) {
	// pathName = json.getString("pathName");
	// }
	// if (json.containsKey("fileDescriptor")) {
	// Document fileDescriptor = (Document) json.get("fileDescriptor");
	// if (fileDescriptor.containsKey("lastModified")) {
	// lastModified = fileDescriptor.getLong("lastModified");
	// }
	// if (fileDescriptor.containsKey("md5")) {
	// md5 = fileDescriptor.getString("md5");
	// }
	// if (fileDescriptor.containsKey("fileSize")) {
	// fileSize = fileDescriptor.getLong("fileSize");
	// }
	// }
	// if (json.containsKey("status")) {
	// status = json.getBoolean("status");
	// }
	// if (json.containsKey("message")) {
	// message = json.getString("message");
	// }
	// if (json.containsKey("content")) {
	// content = json.getString("content");
	// }
	// if (json.containsKey("position")) {
	// position = json.getLong("position");
	// }
	// if (json.containsKey("length")) {
	// length = json.getLong("length");
	// }
	// }

	/**
	 * append general info when creating response protocol
	 * 
	 * @param json
	 * @param cmd
	 * @param message
	 * @param status
	 */
	public void appendResponseInfo(Document json, String cmd, String message, boolean status) {
		json.replace("command", cmd);
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
						appendResponseInfo(json1, "FILE_CREATE_RESPONSE", message, result);

						Document json2 = Document.parse(json.toJson());
						json2.replace("command", "FILE_BYTES_REQUEST");
						json2.append("position", 0);
						json2.append("length", BLOCKSIZE);

						responses.add(json1);
						responses.add(json2);

						return responses;
					}
				}else {
					message = "file loader already exists";
				}
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} 
//			finally {
//				appendResponseInfo(json, "FILE_CREATE_RESPONSE", message, result);
//				responses.add(json);
//				return responses;
//			}
		}

		appendResponseInfo(json, "FILE_CREATE_RESPONSE", message, result);
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
			ByteBuffer buffer = fileSystemManager.readFile(md5, position,
					(fileSize - position) > BLOCKSIZE ? BLOCKSIZE : (fileSize - position));
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
//		finally {
//			appendResponseInfo(json, "FILE_BYTES_RESPONSE", message, result);
//			responses.add(json);
//			return responses;
//		}
		 appendResponseInfo(json, "FILE_BYTES_RESPONSE", message, result);
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

		boolean result = false;

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
					long p = json.getLong("position");
					json.remove("position");
					json.append("position", (p + buffer.capacity()));
					responses.add(json);
				}

			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		// json.replace("command", "FILE_BYTES_REQUEST");
		// json.remove("content");
		// json.remove("message");
		// json.remove("status");
		// long p = json.getLong("position");
		// json.remove("position");
		// json.append("position", (p + buffer.capacity()));
		return responses;
	}
}
