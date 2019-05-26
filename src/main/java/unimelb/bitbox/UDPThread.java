package unimelb.bitbox;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
		String msg;
		try {
			msg = new String(received.getData(),0,received.getLength(),"UTF-8");
			if (msg != null) {
				
				
				try{
					List<Document> responses = handler.handleMsg(msg);
					if (responses !=null) {
						for (Document d : responses) {
							byte[] data = d.toJson().getBytes("UTF-8");
							log.warning("Actual data length: "+data.length);
							DatagramPacket response = new DatagramPacket(data, data.length, received.getAddress(), received.getPort());
							ds.send(response);
						}
					}
				}catch (IOException e) {
					log.warning(e.getMessage());
				}catch (NullPointerException npe) {
					log.warning("caught npe when processing this msg: "+msg);
				}
			}
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}
}
