/*
 * The Lock Object to monitor the state of one Lock request 
 */

package activitystreamer.server;

import java.util.ArrayList;

public class LockItem {
	private String userName;
	private String secret;
	private Long registerTime;
	// if one server send lock_request originally, then this var store the client connection
	// otherwise store the receive server connection
	private Connection originCon; 
	// the broadcast times
	private ArrayList<String> outConIP;
	
	public LockItem(String name, String sec, Long rTime, Connection con, ArrayList<String> outConip) {
		userName  = name;
		secret    = sec;
		originCon = con; 
		outConIP  = outConip;
		registerTime = rTime;
	}
	
	
	public Connection getOriginCon() {
		return originCon;
	}
	
	// check if need to reply the lock response to origin connection 
	public boolean ifNeedReplyOrginCon (Connection receiveCon) {
		outConIP.remove(receiveCon.getIPAddressWithPort());
		return outConIP.size() == 0;
	}

	public String getSecret() {
		return secret;
	}


	public String getUserName() {
		return userName;
	}
	
	public Long getRegisterTime () {
		return registerTime;
	}
}
