package activitystreamer.server;

import java.util.ArrayList;
import java.util.Date;

/*
 * Anonymous identify with ip (username is "") and normal user identify with username 
 */
public class UserInfo {
	
	private ArrayList<String> messageArray = null;
	private String username = "";
	private String secret = "";
	private String ipAddress = "";
	
	public UserInfo(String name, String sec, String ip) {
		setUsername(name);
		setIpAddress(ip);
		setSecret(sec);
		setMessageArray(new ArrayList<String>());
	}
	
	public String getIpAddress() {
		return ipAddress;
	}
	
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	
	public String getUsername() {
		return username;
	}
	
	public void setUsername(String username) {
		this.username = username;
	}
	
	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}
	
	public ArrayList<String> getMessageArray() {
		return messageArray;
	}

	public void setMessageArray(ArrayList<String> messageArray) {
		this.messageArray = messageArray;
	}
	
	/*
	 * if username is anonymous, then check if ip address is similar
	 * else check if username is similar
	 */
	@Override
	public boolean equals (Object usr) {
		if (!usr.getClass().equals(UserInfo.class)) {
			return false;
		}
		
		UserInfo user = (UserInfo) usr;
		if (user.isAnonymous()) {
			return user.getIpAddress().equals(ipAddress);
		}
		else {
			return user.getUsername().equals(username);
		}
	}
	
	/*
	 * check if user is anonymous
	 */
	public boolean isAnonymous() {
		return username.equals("anonymous");
	}
}

/*
 * logout user Info 
 */
class LogoutUserInfo extends UserInfo {

	private long lastLogoutTime = 0;
	
	public LogoutUserInfo(String name, String sec, String ip) {
		super(name, sec, ip);
		setLastLogoutTime(new Date().getTime());
	}
	
	public long getLastLogoutTime() {
		return lastLogoutTime;
	}
	
	public void setLastLogoutTime(long lastLogoutTime) {
		this.lastLogoutTime = lastLogoutTime;
	}
}

/*
 * login user Info 
 */
class LoginUserInfo extends UserInfo {
	private Connection connection = null;
	private int latestIndex = 0;
	
	public LoginUserInfo(String name, String sec, Connection con) {
		super(name, sec, con.getIPAddressWithPort());
		connection = con;
	}

	public Connection getConnection() {
		return connection;
	}

	public void setConnection(Connection connection) {
		this.connection = connection;
	}

	public int getLatestIndex() {
		return latestIndex;
	}

	public void setLatestIndex(int latestIndex) {
		this.latestIndex = latestIndex;
	}
	
	public int increaseIndex () {
		return ++this.latestIndex;
	}
}
