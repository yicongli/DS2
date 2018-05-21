package activitystreamer.server;
import java.util.ArrayList;
import activitystreamer.util.Settings;

public class UserManager {
	
	private ArrayList<LogoutUserInfo> logoutUserInfos = null;
	private ArrayList<LoginUserInfo> loginUserInfos = null;
	
	public UserManager() {
		setLogoutUserInfos(new ArrayList<LogoutUserInfo>());
		setLoginUserInfos(new ArrayList<LoginUserInfo>());
	}
	
	public ArrayList<LoginUserInfo> getLoginUserInfos() {
		return loginUserInfos;
	}

	public void setLoginUserInfos(ArrayList<LoginUserInfo> loginUserInfos) {
		this.loginUserInfos = loginUserInfos;
	}

	public ArrayList<LogoutUserInfo> getLogoutUserInfos() {
		return logoutUserInfos;
	}

	public void setLogoutUserInfos(ArrayList<LogoutUserInfo> logoutUserInfos) {
		this.logoutUserInfos = logoutUserInfos;
	}
	
	public void saveLogoutTime(LoginUserInfo userInfo) {
		String ip = Settings.socketAddress(userInfo.getConnection().getSocket());
		logoutUserInfos.add(new LogoutUserInfo(userInfo.getUsername(), userInfo.getSecret(), ip));
	}
	
	public synchronized void addNewLoginUserInfo(String username, String secret, Connection con) {
		getLoginUserInfos().add(new LoginUserInfo(username, secret, con));
	}
	
	/*
	 * add by yicongLI check if can operate authentication
	 * */
	public int shouldAuthenticateUser (String username, String secret, Connection con) {
		int latestIndex = -1;
		for (LoginUserInfo userInfo : getLoginUserInfos()) {
			if (userInfo.getUsername().equals(username) 
					&& userInfo.getSecret().equals(secret)
						&& userInfo.getConnection().equals(con)) {
				latestIndex = userInfo.increaseIndex();
			}
		}
		
		return latestIndex;
	}
	
	/*
	 * remove login user when connection lost or receive logout message
	 */
	public synchronized boolean removeLoginUserInfo (Connection con) {
		LoginUserInfo curUserInfo = null;
		for (LoginUserInfo userInfo : getLoginUserInfos()) {
			if (userInfo.getConnection().equals(con)) {
				curUserInfo = userInfo;
				saveLogoutTime(userInfo);
			}
		}
		
		return curUserInfo != null;
	}
}
