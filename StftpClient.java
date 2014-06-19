import java.io.*;
import java.net.*;

/**
 * 
 * @author Thomas Verstappen
 *
 */
public class StftpClient {
	private StftpServer server = new StftpServer();
    private byte[] receiveData = new byte[1472];
    private long fileSize = -1;
    private static String fileName = "test.txt";
    private InetAddress IPAddress;
    private static DatagramSocket clientSocket;
    private int serverPort;

	
	public static void main(String[] args) {
		StftpClient client = new StftpClient();
		
		try {
			clientSocket  = new DatagramSocket();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		if(args.length == 1){
			fileName = args[0];
		}else{
			System.err.println("Wrong amount of arguments entered, required arguments are:");
			System.err.println("\t<File name>");
			System.exit(1);
		}
		
		client.run();
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
	
	private void run(){
		try {
			IPAddress = InetAddress.getByName("localhost");
		    
		    DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
		    clientSocket.setSoTimeout(5000);
		    File file = new File(fileName);
		    FileOutputStream fos = new FileOutputStream(file);
		    
		    sendREQ();
		    
		    try{
			    clientSocket.receive(receivePacket);
			    serverPort = receivePacket.getPort();
			    
			    if(checkOK(receiveData) != true){
			    	fos.write(receiveData, 9, receivePacket.getLength()-9);
			    	
			    	System.out.println(receivePacket.getLength());
			    	
			    	sendACK(extractLong(receiveData, 1));
			    }	    
			    
			    do{
		    		clientSocket.receive(receivePacket);
		    		
		    		fos.flush();
			    	fos.write(receiveData, 9, receivePacket.getLength()-9);
			    	
			    	sendACK(extractLong(receiveData, 1));
			    }while(receivePacket.getLength() == 1472);
			    
			    fos.close();
		    	clientSocket.close();
		    	
		    	System.out.println("File transfer completed!");
		    }catch (SocketTimeoutException e) {
		        System.err.println("No response from server, NOTOK or REQ packet lost");
		        System.err.println("EXITING");
		        System.exit(1);
		    }
		    
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends an REQ packet to the already pre-defined address and port
	 * @throws IOException
	 */
	private void sendREQ() throws IOException{
		System.out.println("Creating REQ packet requesting file: " + fileName);
		byte[] REQ = new byte[1472];
		REQ[0] = 3;
		int point = 1;
		for(byte b : fileName.getBytes()){
			REQ[point] = b;
			point++;
		}
	    
	    DatagramPacket REQPacket = new DatagramPacket(REQ, REQ.length, IPAddress, 10001);
	    REQPacket.setLength(point);
	    clientSocket.send(REQPacket);
	}
	
	/**
	 * Sends an ACK packet to the already pre-defined address and port
	 * @param long seqNum
	 * @throws IOException
	 */
	private void sendACK(long seqNum) throws IOException{
		byte[] ACK = new byte[1472];
		ACK[0] = 43;
		
		storeLong(ACK, 1, seqNum);
	    
	    DatagramPacket REQPacket = new DatagramPacket(ACK, ACK.length, IPAddress, serverPort);
	    REQPacket.setLength(9);
	    clientSocket.send(REQPacket);
	}
	
	/**
	 * Checks if the array of bytes given as input are either an OK or a NOTOK packet
	 * @param byte[] data
	 * @return True if the packet is an OK packet, false if not
	 */
	private Boolean checkOK(byte[] data){
		if(receiveData[0] == 0){
	    	System.err.println("Error: invalid file on server");
	    	System.exit(1);
	    }else if (receiveData[0] == 1){
	    	fileSize = extractLong(receiveData, 1);
	    	
	    	if(fileSize > 1024){
	    		if(fileSize > 1048576){
	    			System.out.println("File size: " + (fileSize / 1048576) + "Mb");
	    		}else{
	    			System.out.println("File size: " + (fileSize / 1024) + "Kb");
	    		}
	    	}else{
	    		System.out.println("File size: " + fileSize + " bytes");
	    	}
	    	
	    	return true;
	    }
		return false;
	}
}
