package activitystreamer.server;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.util.Settings;


public class Connection extends Thread {
	private static final Logger log = LogManager.getLogger();
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean open = false;
	private Socket socket;
	private boolean term = false;
	private boolean isServer = false; 		// indicate if the connection is client, default is not sever
	private boolean isRemoteServer = false; // indicate if the connection is the remote server of current server
	private String secrete  = ""; 			// store the secrete for Server
	private Long   remoteLitenerPort = (long) 0; // the listener port of remote server
	
	Connection(Socket socket) throws IOException{
		in = new DataInputStream(socket.getInputStream());
	    out = new DataOutputStream(socket.getOutputStream());
	    inreader = new BufferedReader( new InputStreamReader(in));
	    outwriter = new PrintWriter(out, true);
	    this.socket = socket;
	    open = true;
	    start();
	}
	
	/*
	 * returns true if the message was written, otherwise false
	 */
	public boolean writeMsg(String msg) {
		if(open){
			outwriter.println(msg);
			outwriter.flush();
			return true;	
		}
		return false;
	}
	
	public void closeCon(){
		if(open){
			log.info("closing connection "+getIPAddressWithPort());
			try {
				term=true;
				inreader.close();
				out.close();
			} catch (IOException e) {
				// already closed?
				log.error("received exception closing the connection "+getIPAddressWithPort()+": "+e);
			}
		}
	}
	
	
	public void run(){
		try {

			// maybe server to client messages go from here (?)
			String data;
			while(!term && (data = inreader.readLine())!=null){
				term=Control.getInstance().process(this,data);
			}
			
			// remove login user info when log out
			Control.clientInfoManager.removeLoginClientInfo(this); 
			
			log.debug("connection closed to "+ getIPAddressWithPort());
			Control.getInstance().connectionClosed(this);
			in.close();
		} catch (IOException e) {
			log.error("connection "+getIPAddressWithPort()+" closed with exception: "+e);
			Control.getInstance().connectionClosed(this);
		}
		open=false;
	}
	
	public boolean getIsServer () {
		return isServer;
	}
	
	public void setIsServer (boolean isS) {
		isServer = isS;
	}
	
	public Socket getSocket() {
		return socket;
	}
	
	public boolean isOpen() {
		return open;
	}
	
	public String secrete () {
		return secrete;
	}
	
	public void setSecret (String se) {
		secrete = se;
	}

	public boolean getIsRemoteServer() {
		return isRemoteServer;
	}

	public void setIsRemoteServer(boolean isRServer) {
		this.isRemoteServer = isRServer;
	}

	public Long getRemoteLitenerPort() {
		return remoteLitenerPort;
	}

	public void setRemoteLitenerPort(Long remoteLitenerPort) {
		this.remoteLitenerPort = remoteLitenerPort;
	}
	
	public String getIPAddress () {
		String IP = getSocket().getInetAddress().toString();
		// if the server is in same IP address with current server, then get the current external IP
		return IP.equals("/127.0.0.1") ? Settings.getIp() : IP.substring(1, IP.length()-1);
	}
	
	public String getIPAddressWithPort () {
		String IP = getIPAddress();
		return IP + ":" + socket.getPort();
	}
}
