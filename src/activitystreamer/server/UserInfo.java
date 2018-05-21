package activitystreamer.server;

import java.util.ArrayList;
import java.util.Date;

/*
 * Anonymous identify with ip (username is "") and normal user identify with username 
 */
public class UserInfo {
	
	private ArrayList<String> messageArray = null;
	private String username = "";
	private String ipAddress = "";
	
	public UserInfo(String name, String ip) {
		setUsername(name);
		setIpAddress(ip);
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
	
	public ArrayList<String> getMessageArray() {
		return messageArray;
	}

	public void setMessageArray(ArrayList<String> messageArray) {
		this.messageArray = messageArray;
	}
}

class LogoutUserInfo extends UserInfo {

	private Date lastLogoutTime = null;
	
	public LogoutUserInfo(String name, String ip) {
		super(name, ip);
		setLastLogoutTime(new Date());
	}
	
	public Date getLastLogoutTime() {
		return lastLogoutTime;
	}
	
	public void setLastLogoutTime(Date lastLogoutTime) {
		this.lastLogoutTime = lastLogoutTime;
	}
}

class LoginUserInfo extends UserInfo {
	public LoginUserInfo(String name, String ip) {
		super(name, ip);
	}
}
