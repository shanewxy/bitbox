# bitbox
Project 1 and 2 of Distributed System.

A distributed file share system. The basic components are the File System Manager that monitors a given directory in the file system on a local machine, for changes to files, etc., and a BitBox Peer that can "relay" these changes to another BitBox Peer on a remote machine.

We would like to allow the BitBox Peers to form an unstructured P2P network. By unstructured we mean that the connection pattern has no relevance to the functionality, and is somewhat arbitrary depending on how and when peers come and go.

#Peer Protocol Messages

INVALID_PROTOCOL

CONNECTION_REFUSED

HANDSHAKE_REQUEST, HANDSHAKE_RESPONSE

FILE_CREATE_REQUEST, FILE_CREATE_RESPONSE

FILE_DELETE_REQUEST, FILE_DELETE_RESPONSE

FILE_MODIFY_REQUEST, FILE_MODIFY_RESPONSE

DIRECTORY_CREATE_REQUEST, DIRECTORY_CREATE_RESPONSE

DIRECTORY_DELETE_REQUEST, DIRECTORY_DELETE_RESPONSE

FILE_BYTES_REQUEST, FILE_BYTES_RESPONSE