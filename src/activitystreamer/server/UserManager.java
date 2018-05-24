package activitystreamer.server;
import java.util.ArrayList;

public class UserManager {
	
	private ArrayList<LogoutUserInfo> logoutUserInfos = null;
	private ArrayList<LoginUserInfo> loginUserInfos = null;
	private ArrayList<UserInfo> incomeMessageInfos = null;
	
	public UserManager() {
		setLogoutUserInfos(new ArrayList<LogoutUserInfo>());
		setLoginUserInfos(new ArrayList<LoginUserInfo>());
		setIncomeMessageInfos(new ArrayList<UserInfo>());
	}
	
	public synchronized ArrayList<LoginUserInfo> getLoginUserInfos() {
		return loginUserInfos;
	}

	public synchronized void setLoginUserInfos(ArrayList<LoginUserInfo> loginUserInfos) {
		this.loginUserInfos = loginUserInfos;
	}

	public synchronized ArrayList<LogoutUserInfo> getLogoutUserInfos() {
		return logoutUserInfos;
	}

	public synchronized void setLogoutUserInfos(ArrayList<LogoutUserInfo> logoutUserInfos) {
		this.logoutUserInfos = logoutUserInfos;
	}
	
	public ArrayList<UserInfo> getIncomeMessageInfos() {
		return incomeMessageInfos;
	}

	public void setIncomeMessageInfos(ArrayList<UserInfo> incomeMessageInfos) {
		this.incomeMessageInfos = incomeMessageInfos;
	}

	public synchronized void saveLogoutTime(LoginUserInfo userInfo) {
		logoutUserInfos.add(new LogoutUserInfo(userInfo.getUsername(), userInfo.getSecret(), userInfo.getIpAddress()));
	}
	
	public synchronized void addNewLoginUserInfo(String username, String secret, Connection con) {
		getLoginUserInfos().add(new LoginUserInfo(username, secret, con));
	}
	
	/*
	 * add by yicongLI check if can operate authentication
	 * */
	public synchronized int shouldAuthenticateUser (String username, String secret, Connection con) {
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
		if (con.getIsServer()) {
			return false;
		}
		
		LoginUserInfo curUserInfo = null;
		for (LoginUserInfo userInfo : getLoginUserInfos()) {
			if (userInfo.getConnection().equals(con)) {
				curUserInfo = userInfo;
				saveLogoutTime(userInfo);
			}
		}
		
		if (curUserInfo != null) {
			getLoginUserInfos().remove(curUserInfo);
		}
		
		return curUserInfo != null;
	}
}
