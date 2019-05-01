package unimelb.bitbox;

public enum Command {
	INVALID_PROTOCOL,
	CONNECTION_REFUSED,
	HANDSHAKE_REQUEST, 
	HANDSHAKE_RESPONSE,
	FILE_CREATE_REQUEST, 
	FILE_CREATE_RESPONSE,
	FILE_DELETE_REQUEST, 
	FILE_DELETE_RESPONSE,
	FILE_MODIFY_REQUEST, 
	FILE_MODIFY_RESPONSE,
	DIRECTORY_CREATE_REQUEST, 
	DIRECTORY_CREATE_RESPONSE ,
	DIRECTORY_DELETE_REQUEST, 
	DIRECTORY_DELETE_RESPONSE ,
	FILE_BYTES_REQUEST, 
	FILE_BYTES_RESPONSE
}