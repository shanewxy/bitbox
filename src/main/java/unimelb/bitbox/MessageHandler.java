package unimelb.bitbox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class MessageHandler {

	private static final Long BLOCKSIZE = Long.parseLong(Configuration.getConfigurationValue("blockSize"));

	static String cmd = null;
	static String pathName = null;
	static Long lastModified = null;
	static String md5 = null;
	static Long fileSize = null;
	FileSystemManager fileSystemManager;
	static String host = null;
	static Long port;
	static boolean status;
	static String message;
	static String content;
	static Long length;
	static Long position;

	public MessageHandler(FileSystemManager fileSystemManager) {
		this.fileSystemManager = fileSystemManager;

	}

	/**
	 * handle incoming message
	 */
	public ArrayList<Document> handleMsg(String msg) {
		System.out.println(msg);
		parseJsonMsg(msg);

		boolean result = false;
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
			//
			if (!fileSystemManager.isSafePathName(pathName)) {
				Document json = Document.parse(msg);
				json.replace("command", "FILE_CREATE_RESPONSE");
				json.append("message", "unsafe pathname given");
				json.append("status", false);
				result = false;
				// System.out.println(json.toJson());
			}
			if (fileSystemManager.fileNameExists(pathName)) {
				Document json = Document.parse(msg);
				json.replace("command", "FILE_CREATE_RESPONSE");
				json.append("message", "pathname already exists");
				json.append("status", false);
				result = false;
			} else {
				ArrayList<Document> responses = new ArrayList<>();
				try {
					// System.out.println(pathName+" "+md5+" "+
					// length+lastModified);
					if (fileSystemManager.createFileLoader(pathName, md5, BLOCKSIZE, lastModified)) {
//						if (fileSystemManager.checkShortcut(pathName)) {
//							// TODO use a local copy
//						}
//						else 
						{

							Document json1 = Document.parse(msg);
							json1.replace("command", "FILE_CREATE_RESPONSE");
							json1.append("message", "file loader ready");
							json1.append("status", true);
							System.out.println("need to send" + json1.toJson());

							Document json2 = Document.parse(msg);
							json2.replace("command", "FILE_BITES_REQUEST");
							json2.append("position", 0);
							json2.append("length", BLOCKSIZE);
							System.out.println("need to send" + json2.toJson());

							responses.add(json1);
							responses.add(json2);
						}
					}
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				result = true;

				return responses;
			}
			break;
		case "FILE_BITES_REQUEST":
			System.out.println("received file bites requests");
			try {
				ByteBuffer buffer = fileSystemManager.readFile(md5, position, length);
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		case "FILE_BITES_RESPONSE":
			//
			// fileSystemManager.writeFile(pathName, src, position);
			break;
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
	 * marshall the result and event into Json
	 */

	/**
	 * retrive message from received Json and store info into variables
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
				this.fileSize = fileDescriptor.getLong("fileSize");
			}
		}
		if (json.containsKey("hostPort")) {
			Document hostPort = (Document) json.get("hostPort");
			if (hostPort.containsKey(host)) {
				this.host = hostPort.getString("host");
			} else if (hostPort.containsKey("port")) {
				this.port = hostPort.getLong("port");
			}
		}
		if (json.containsKey("status")) {
			this.status = json.getBoolean("status");
		}
		if (json.containsKey("message")) {
			this.message = json.getString("message");
		}
		if (json.containsKey("content")) {
			this.content = json.getString("content");
		}
		if (json.containsKey("position")) {
			this.position = json.getLong("position");
		}
		if (json.containsKey("length")) {
			this.length = json.getLong("length");
		}
	}
}
