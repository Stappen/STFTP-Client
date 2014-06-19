import java.net.*;
import java.io.*;
import java.util.*;

/**
 * 
 * @author Thomas Verstappen
 *
 */
public class StftpServer {
	public static void main(String args[]) {
		StftpServer d = new StftpServer();
		d.startServer();
	}
	
	public void startServer() {
		try {
			DatagramSocket ds = new DatagramSocket(10001);
			System.out.println("StftpServer on port " + ds.getLocalPort());

			for (;;) {
				byte[] buf = new byte[1472];
				DatagramPacket p = new DatagramPacket(buf, 1472);
				ds.receive(p);

				StftpServerWorker worker = new StftpServerWorker(p);
				worker.start();
			}
		} catch (Exception e) {
			System.err.println("Exception: " + e);
		}

		return;
	}
}

class StftpServerWorker extends Thread {
	private DatagramPacket req;
	private DatagramSocket serverSocket;
	private int ReceivedPort;
	private InetAddress ReceivedIP;
	private static final int REQ = 3;
	private static final int OK = 1;
	private static final int DATA = 2;
	private static final int ACK = 42;
	private static final int NOTOK = 0;
	private long fileSize;
	private File file;
	
	public StftpServerWorker(DatagramPacket req) {
		this.req = req;
		this.ReceivedPort = req.getPort();
		this.ReceivedIP = req.getAddress();
		try {
			serverSocket = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	private static void storeLong(byte[] array, int off, long val) {
		array[off + 0] = (byte) ((val & 0xff00000000000000L) >> 56);
		array[off + 1] = (byte) ((val & 0x00ff000000000000L) >> 48);
		array[off + 2] = (byte) ((val & 0x0000ff0000000000L) >> 40);
		array[off + 3] = (byte) ((val & 0x000000ff00000000L) >> 32);
		array[off + 4] = (byte) ((val & 0x00000000ff000000L) >> 24);
		array[off + 5] = (byte) ((val & 0x0000000000ff0000L) >> 16);
		array[off + 6] = (byte) ((val & 0x000000000000ff00L) >> 8);
		array[off + 7] = (byte) ((val & 0x00000000000000ffL));
		return;
	}

	private static long extractLong(byte[] array, int off) {
		long a = array[off + 0] & 0xff;
		long b = array[off + 1] & 0xff;
		long c = array[off + 2] & 0xff;
		long d = array[off + 3] & 0xff;
		long e = array[off + 4] & 0xff;
		long f = array[off + 5] & 0xff;
		long g = array[off + 6] & 0xff;
		long h = array[off + 7] & 0xff;
		return (a << 56 | b << 48 | c << 40 | d << 32 | e << 24 | f << 16
				| g << 8 | h);
	}

	/**
	 * Opens the file and calls sendData() to begin sending data
	 * @param String filename
	 */
	private void sendfile(String filename) {
		
		
		try {
			if (filename.contains("/")) {
				throw new FileNotFoundException();
			}
			
			//Set a new file object as the file name that was requested
			file = new File("Files/" + filename);
			
			if(!file.exists()){
				sendNOTOK();
			}else{
				sendOK();
			}
			
			sendData();
			
			serverSocket.close();

		} catch (FileNotFoundException e) {
			System.err.println("Error: file not found.");
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Parses the req packet, ensuring that it is a request, and then call sendfile with the filename requested
	 * 
	 * Calls sendfile() once completed and passes on the file name for sending
	 */
	public void run() {
		String fileName = "";
		byte[] bytes = req.getData();
		for(int i = 1; i < req.getLength(); i++){
			fileName += (char)bytes[i];
		}
		
		System.out.println("The server got a request for '" + fileName + "' from port " + req.getPort() + " and IP " + req.getAddress());
		
		sendfile(fileName);
	}
	
	/**
	 * Returns the op code of the Datagram packet passed into the method
	 * @param DatagramPacket
	 * @return int
	 */
	private int getOPCode(DatagramPacket packet){
		System.out.println("Opcode: " + packet.getData()[0]);
		return packet.getData()[0];
	}
	
	/**
	 * When called, send a NOTOK packet to the connection and port already received
	 */
	private void sendNOTOK() throws IOException{
		byte[] NOTOK = new byte[1472];
		NOTOK[0] = 0;
		
		DatagramPacket NOTOKPacket = new DatagramPacket(NOTOK, NOTOK.length, ReceivedIP, ReceivedPort);
		NOTOKPacket.setLength(1);
		serverSocket.send(NOTOKPacket);
		
	}
	
	/**
	 * When called, send an OK packet to the connection and port already received
	 */
	private void sendOK() throws IOException{
		//Set a long as the length of the file
		fileSize = file.length();
		
		byte[] OK = new byte[1472];
		OK[0] = 1;
		storeLong(OK, 1, fileSize);
		
	    DatagramPacket OKPacket = new DatagramPacket(OK, OK.length, ReceivedIP, ReceivedPort);
	    OKPacket.setLength(9);
	    serverSocket.send(OKPacket);
	    
	}
	
	/**
	 * Sends the data from the file 1 packet at a time to the client
	 * @throws IOException
	 */
	private void sendData() throws IOException{
		FileInputStream fis = new FileInputStream(file);
		byte[] sendBuf = new byte[1472];
		byte[] receiveBuf = new byte[1472];
		int length;
		long seqNum = 0;
		
		DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);
		serverSocket.setSoTimeout(1000);
		
		while(true){
			sendBuf[0] = DATA;
			storeLong(sendBuf, 1, seqNum);
			
			length = fis.read(sendBuf, 9, 1463);
			
			if(length == -1){
				break;
			}
			
			
			
		    DatagramPacket DataPacket = new DatagramPacket(sendBuf, length+9, ReceivedIP, ReceivedPort);
		    DataPacket.setLength(length+9);
		    serverSocket.send(DataPacket);
		    
		    short retry = 1;
		    while(true){
		    	
			    try{
			    	serverSocket.receive(receivePacket);
			    	
			    	if(receiveBuf[0] == 42){
			    		if(extractLong(receiveBuf, 1) == seqNum){
			    		    seqNum += length;
			    			continue;
			    		}else{
			    			throw new SocketTimeoutException();
			    		}
			    	}
			    	
			    	break;
			    }
			    catch(SocketTimeoutException e){
			    	//If the server tried to resend the packet 10 times and failed, send an error and quit
			    	if(retry > 10){
			    		System.err.println("ERROR: TRIED TO RESEND 10 TIMES AND FAILED");
			    		System.err.println("EXITING");
			    		System.exit(1);
			    	}
			    	
			    	System.err.println("Retrying to send packet " + seqNum + " " + retry + " time(s)");
			    	retry++;
			    	serverSocket.send(DataPacket);
			    }
		    }
		    
		}
	}
}
