package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;
import java.util.logging.Logger;

import unimelb.bitbox.util.Document;

/**
 * handle received msg and handle retransmission(not complete yet)
 * @author pengkedi
 *
 */
public class UDPThread extends Thread{
	
	private static Logger log = Logger.getLogger(UDPThread.class.getName());
	private DatagramPacket received;
	private DatagramSocket ds;
	private MessageHandler handler;
	private boolean receivedResponse = false;
	private int attempts = 0;

	public UDPThread(DatagramPacket received, DatagramSocket ds, MessageHandler handler) {
		this.ds = ds;
		this.received = received;
		this.handler = handler;
	}

	@Override
	public void run() {
		
		
		// read data from packet
		String msg = new String(received.getData(),0,received.getLength());
		if (msg != null) {
			
			List<Document> responses = handler.handleMsg(msg);
			try{
				if (responses !=null) {
					for (Document d : responses) {
						byte[] data = d.toJson().getBytes();
						DatagramPacket response = new DatagramPacket(data, data.length, received.getAddress(), received.getPort());
						ds.send(response);
					}
				}
			}catch (IOException e) {
				log.warning(e.getMessage());
			}
		}
		
	}
}
