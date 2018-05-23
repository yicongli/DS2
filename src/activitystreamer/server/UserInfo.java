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
}

/*
 * logout user Info 
 */
class LogoutUserInfo extends UserInfo {

	private Date lastLogoutTime = null;
	
	public LogoutUserInfo(String name, String sec, String ip) {
		super(name, sec, ip);
		setLastLogoutTime(new Date());
	}
	
	public Date getLastLogoutTime() {
		return lastLogoutTime;
	}
	
	public void setLastLogoutTime(Date lastLogoutTime) {
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
