package unimelb.bitbox.util;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;

public class Protocol {
	
	private final static String HANDSHAKE_REQUEST = "HANDSHAKE_REQUEST";
	private final static String FILE_MODIFY_REQUEST = "FILE_MODIFY_REQUEST";
	private final static String FILE_MODIFY_RESPONSE = "FILE_MODIFY_RESPONSE";
	private final static String FILE_BITES_REQUEST = "FILE_BITES_REQUEST";
	private final static String FILE_BITES_RESPONSE = "FILE_BITES_RESPONSE";
	
	public static Document handshakeRequest(String address, int port){
		Document json = new Document();
		json.append("command", HANDSHAKE_REQUEST);
		
		Document hostPort = new Document();
		hostPort.append("host", address);
		hostPort.append("port", port);
		
		json.append("hostPort", hostPort);
		
		return json;
	}

	/**
	 * Protocol FILE_MODIFY_REQUEST
	 * 
	 * @param file
	 * @return
	 */
	public static Document fileModifyRequest(File file) {
		Document json = new Document();
		Document fileDescriptor = new Document();

		fileDescriptor.append("md5", getFileMD5(file));
		fileDescriptor.append("lastModifed", file.lastModified());
		fileDescriptor.append("fileSize", file.length());

		json.append("command", FILE_MODIFY_REQUEST);
		json.append("fileDescriptor", fileDescriptor);
		json.append("pathName", file.getName());
		return json;
	}

	public static Document fileModifyResponse(File file, String msg, Boolean status) {
		Document json = new Document();
		Document fileDescriptor = new Document();

		fileDescriptor.append("md5", getFileMD5(file));
		fileDescriptor.append("lastModifed", file.lastModified());
		fileDescriptor.append("fileSize", file.length());

		json.append("command", FILE_MODIFY_RESPONSE);
		json.append("fileDescriptor", fileDescriptor);
		json.append("pathName", file.getName());
		json.append("message", msg);
		json.append("status", status);

		return json;
	}

	public static Document fileBitesRequest(File file, int position, int length) {
		Document json = new Document();
		Document fileDescriptor = new Document();

		fileDescriptor.append("md5", getFileMD5(file));
		fileDescriptor.append("lastModifed", file.lastModified());
		fileDescriptor.append("fileSize", file.length());

		json.append("command", FILE_BITES_REQUEST);
		json.append("fileDescriptor", fileDescriptor);
		json.append("pathName", file.getName());
		json.append("position", position);
		json.append("length", length);

		return json;
	}

	public static Document fileBitesResponse(File file, int position, int length, String msg, Boolean status) {
		Document json = new Document();
		Document fileDescriptor = new Document();

		fileDescriptor.append("md5", getFileMD5(file));
		fileDescriptor.append("lastModifed", file.lastModified());
		fileDescriptor.append("fileSize", file.length());

		json.append("command", FILE_BITES_RESPONSE);
		json.append("fileDescriptor", fileDescriptor);
		json.append("pathName", file.getName());
		json.append("position", position);
		json.append("length", length);
		json.append("message", msg);
		json.append("status", status);

		return json;
	}

	/**
	 * get bin file's md5 string
	 * 
	 * @param file
	 * @return
	 */
	public static String getFileMD5(File file) {
		if (!file.isFile()) {
			return null;
		}
		MessageDigest digest = null;
		FileInputStream in = null;
		byte buffer[] = new byte[1024];
		int len;
		try {
			digest = MessageDigest.getInstance("MD5");
			in = new FileInputStream(file);
			while ((len = in.read(buffer, 0, 1024)) != -1) {
				digest.update(buffer, 0, len);
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		BigInteger bigInt = new BigInteger(1, digest.digest());
		return bigInt.toString(16);
	}
}
