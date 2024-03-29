package activitystreamer.server;

import java.util.ArrayList;
import java.util.Date;

/*
 * Anonymous identify with ip (username is "") and normal user identify with username 
 */
public class ClientInfo {
	
	private ArrayList<String> messageArray = null;	// message array
	private String username = "";					// user name
	private String ipAddress = "";					// ip
	
	public ClientInfo(String name, String ip) {
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
	
	/*
	 * if username is anonymous, then check if ip address is similar
	 * else check if username is similar
	 */
	@Override
	public boolean equals (Object usr) {
		if (!usr.getClass().getSuperclass().equals(ClientInfo.class) 
				&& !usr.getClass().equals(ClientInfo.class)) {
			return false;
		}
		
		ClientInfo user = (ClientInfo) usr;
		if (user.isAnonymous()) {
			return user.getIpAddress().equals(ipAddress);
		}
		else {
			return user.getUsername().equals(username);
		}
	}
	
	/**
	 * Identify if the username or ip identify current user information
	 * @param name income user name
	 * @param ip   income ip identify
	 * @return true: is same info; false not the same info
	 */
	public boolean isCurrentInfo(String name, String ip) {
		if (name.equals("anonymous")) {
			return getIpAddress().equals(ip);
		}
		else {
			return getUsername().equals(name);
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
class LogoutClientInfo extends ClientInfo {

	private long lastLogoutTime = 0;
	private boolean logoutFromCurrentServer = true;
	
	public LogoutClientInfo(String name, String ip) {
		super(name,ip);
		setLastLogoutTime(new Date().getTime());
	}
	
	public long getLastLogoutTime() {
		return lastLogoutTime;
	}
	
	public void setLastLogoutTime(long lastLogoutTime) {
		this.lastLogoutTime = lastLogoutTime;
	}

	public boolean isLogoutFromCurrentServer() {
		return logoutFromCurrentServer;
	}

	public void setLogoutFromCurrentServer(boolean logoutFromCurrentServer) {
		this.logoutFromCurrentServer = logoutFromCurrentServer;
	}
}

/*
 * login user Info 
 */
class LoginClientInfo extends ClientInfo {
	private Connection connection = null;   // the connection to the client
	private String secret = "";				// password of current login client
	private int latestIndex = 0;
	
	public LoginClientInfo(String name, String sec, Connection con) {
		super(name, con.getIPAddress());
		connection = con;
		secret = sec;
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

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}
}

/**
 * Store the user info of income activities
 * @author yicongli
 *
 */
class IncomeActicityClientInfo extends ClientInfo {
	private int latestIndex = -1;	// the latestIndex of currently received message
	private int firstIndex = 1000;  // the first index of missing message
	
	public IncomeActicityClientInfo(String name, Connection con) {
		super(name, con.getIPAddress());
	}

	public int getLatestIndex() {
		return latestIndex;
	}

	public void setLatestIndex(int latestIndex) {
		this.latestIndex = latestIndex;
	}

	public int getFirstIndex() {
		return firstIndex;
	}

	public void setFirstIndex(int firstIndex) {
		this.firstIndex = firstIndex;
	}
	
	/*
	 * reset information of message to default
	 */
	public void resetInfo() {
		this.firstIndex = 1000;
		this.setMessageArray(new ArrayList<String>());
	}
}
