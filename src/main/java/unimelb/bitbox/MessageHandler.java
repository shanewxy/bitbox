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

	String cmd = null;
	String pathName = null;
	long lastModified;
	String md5 = null;
	long fileSize;
	FileSystemManager fileSystemManager;
	boolean status;
	String message;
	String content;
	long length;
	long position;

	public MessageHandler(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;

	}

	/**
	 * handle incoming message
	 */
	public ArrayList<Document> handleMsg(String msg) {
		System.out.println(msg);
		parseJsonMsg(msg);
		ArrayList<Document> responses = new ArrayList<Document>();
		boolean result = false;
		String returnMsg = "";

		switch (cmd) {
		case "FILE_DELETE_REQUEST":
			if (fileSystemManager.fileNameExists(pathName))
				result = fileSystemManager.deleteFile(pathName, lastModified, md5);
			break;
		case "DIRECTORY_CREATE_REQUEST":
			result = fileSystemManager.makeDirectory(pathName);
			break;
		case "DIRECTORY_DELETE_REQUEST":
			result = fileSystemManager.deleteDirectory(pathName);
			break;
		case "FILE_CREATE_REQUEST":
			Document json = Document.parse(msg);
			// ArrayList<Document> responses = new ArrayList<Document>();

			if (!fileSystemManager.isSafePathName(pathName)) {
				returnMsg = "unsafe pathname given";

				// appendResponseInfo(json, "FILE_CREATE_RESPONSE", "unsafe
				// pathname given", false);
				// responses.add(json);
				// return responses;
			} else if (fileSystemManager.fileNameExists(pathName)) {
				returnMsg = "pathname already exists";

				// appendResponseInfo(json, "FILE_CREATE_RESPONSE", "pathname
				// already exists", false);
				// responses.add(json);
				// return responses;
			} else {
				try {
					// if (!fileSystemManager.createFileLoader(pathName, md5,
					// fileSize, lastModified)) {
					// appendResponseInfo(json, "FILE_CREATE_RESPONSE", "file
					// loader already in progress", false);
					// responses.add(json);
					// return responses;
					// } else
					fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified);
					if (fileSystemManager.checkShortcut(pathName)) {
						// use a local copy
						returnMsg = "use a local copy to create the file";
						// appendResponseInfo(json, "FILE_CREATE_RESPONSE", "use
						// a local copy to create the file", true);
						// responses.add(json);
						// return responses;
					} else {
						Document json1 = Document.parse(msg);
						appendResponseInfo(json1, "FILE_CREATE_RESPONSE", "file loader ready", true);

						Document json2 = Document.parse(msg);
						json2.replace("command", "FILE_BYTES_REQUEST");
						json2.append("position", 0);
						json2.append("length", BLOCKSIZE);

						responses.add(json1);
						responses.add(json2);

						return responses;
					}
				} catch (NoSuchAlgorithmException e) {
					returnMsg = e.getMessage();
				} catch (IOException e) {
					returnMsg = e.getMessage();
					// appendResponseInfo(json, "FILE_CREATE_RESPONSE",
					// e.getMessage(), result);
					// responses.add(json);
					// return responses;
				}
				appendResponseInfo(json, "FILE_CREATE_RESPONSE", returnMsg, result);
				responses.add(json);
				return responses;
			}
			break;
		case "FILE_CREATE_RESPONSE":
			log.info("command :" + cmd + "message :" + message + " status : " + status);
			break;
		case "FILE_BYTES_REQUEST":
			Document response = Document.parse(msg);
			try {
				// if (fileSize > BLOCKSIZE) {
				// System.out.println("this file needs to separately");
				// }
				ByteBuffer buffer = fileSystemManager.readFile(md5, position,
						(fileSize - position) > BLOCKSIZE ? BLOCKSIZE : (fileSize - position));
				if (buffer != null) {

					Base64.Encoder encoder = Base64.getEncoder(); // get encoder
					buffer.flip();
					byte[] bytes = new byte[buffer.limit()];
					buffer.get(bytes);
					//content = encoder.encodeToString(bytes);

					response.append("content", encoder.encodeToString(bytes));
					returnMsg = "successful read";
					result = true;
					//appendResponseInfo(response, "FILE_BYTES_RESPONSE", "successful read", true);

				} else {
					returnMsg = "unsuccessful read, failed to read from source file";
//					appendResponseInfo(response, "FILE_BYTES_RESPONSE",
//							"unsuccessful read, failed to read from source file", false);
				}
				// return new ArrayList<Document>() {
				// {
				// add(response);
				// }
				// };

			} catch (NoSuchAlgorithmException e) {
				returnMsg = e.getMessage();
			} catch (IOException e) {
				returnMsg = e.getMessage();
			}
			appendResponseInfo(response, "FILE_BYTES_RESPONSE", returnMsg, result);
			responses.add(response);
			return responses;
			//break;
		case "FILE_BYTES_RESPONSE":
			Document byteRequest = Document.parse(msg);
			
			Base64.Decoder decoder = Base64.getDecoder(); // get decoder
			byte[] bytes = decoder.decode(content);
			ByteBuffer buffer = ByteBuffer.allocate(bytes.length);

			buffer.put(bytes, 0, bytes.length);
			buffer.flip();

			try {
				if (fileSystemManager.writeFile(pathName, buffer, position)) {
					if (fileSystemManager.checkWriteComplete(pathName)) {
						//System.out.println("for test use : wirte done-------");
						break;
					} else {

						byteRequest.replace("command", "FILE_BYTES_REQUEST");
						byteRequest.remove("content");
						byteRequest.remove("message");
						byteRequest.remove("status");
						long p = byteRequest.getLong("position");
						byteRequest.remove("position");
						byteRequest.append("position", (p + buffer.capacity()));
						// System.out.println("tttttest: " +
						// biteRequest.toJson());

						// System.out.println("for test use : not complete
						// yet");

//						return new ArrayList<Document>() {
//							{
//								add(byteRequest);
//							}
//						};
					}
				}
			} catch (NoSuchAlgorithmException e) {
				returnMsg = e.getMessage();
			} catch (IOException e) {
				returnMsg = e.getMessage();
			}
			appendResponseInfo(byteRequest, "FILE_BYTES_REQUEST", returnMsg, result);
			responses.add(byteRequest);
			return responses;
			//break;
		}
		return null;
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
	public void parseJsonMsg(String msg) {
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
			if (fileDescriptor.containsKey("md5")) {
				md5 = fileDescriptor.getString("md5");
			}
			if (fileDescriptor.containsKey("fileSize")) {
				fileSize = fileDescriptor.getLong("fileSize");
			}
		}
		if (json.containsKey("status")) {
			status = json.getBoolean("status");
		}
		if (json.containsKey("message")) {
			message = json.getString("message");
		}
		if (json.containsKey("content")) {
			content = json.getString("content");
		}
		if (json.containsKey("position")) {
			position = json.getLong("position");
		}
		if (json.containsKey("length")) {
			length = json.getLong("length");
		}
	}

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
}
