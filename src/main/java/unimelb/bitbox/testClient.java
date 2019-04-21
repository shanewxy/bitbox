package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
//import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import unimelb.bitbox.util.Document;

public class testClient {

//	public static void test() {
//		try {
//			ServerSocket ss = new ServerSocket(8111);
//			System.out.println("Server started: "+ss.getInetAddress()+":"+ss.getLocalPort());
//			Socket s = ss.accept();
//			System.out.println("got one");
//
//			BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
//			while(true) {
//				String  str = in.readLine();
//				System.out.println("I'm server: "+str);
//			}
//			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//	}
	
	public static void main(String[] args) {
		AtomicInteger i = new AtomicInteger();
		System.out.println(i.get());
		System.out.println(i.incrementAndGet());
		// TODO Auto-generated method stub
//		try(Socket s = new Socket("43.240.97.106",3000);){
//			HashMap test = new HashMap();
//			String t = "haha";
//			test.put("abc", t);
//			long b = (long) test.get("abc");
//			int a = (int) b;
//			System.out.println("Socket established");
//			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
//			Document d = Document.parse("{\"command\":\"HANDSHAKE_REQUEST\",\"hostPort\":{\"host\":\"172.20.10.5\",\"port\":8111}}");
//			System.out.println("Sending...: "+d.toJson());
//			out.write(d.toJson()+"\n");
//			out.flush();
//			
//			System.out.println("Waiting....");
//			BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
//			while(true) {
////				if(in.ready()) {
//					System.out.println(":)");
//					String ans = in.readLine();
//					System.out.println("Received: "+ans);
////				}else {
////					System.out.println(":(");
////					try {
////						Thread.sleep(1000);
////					} catch (InterruptedException e) {
////						// TODO Auto-generated catch block
////						e.printStackTrace();
////					}
////				}
//				
//			}
//		} catch (UnknownHostException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.getMessage();
//		}
	}

}
