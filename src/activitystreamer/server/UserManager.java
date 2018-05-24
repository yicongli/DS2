package activitystreamer.server;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;

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
	
	/**
	 * Add the login user information to the storage and check if need to send cache activities to the client
	 * @param username 	current user's username
	 * @param secret	current user's secret
	 * @param con		current user's con
	 */
	@SuppressWarnings("unchecked")
	public synchronized void addNewLoginUserInfo(String username, String secret, Connection con) {
		LoginUserInfo newUser = new LoginUserInfo(username, secret, con);
		getLoginUserInfos().add(newUser);
		
		// check if local has stored logout user information
		LogoutUserInfo oldUser = null;
		for (LogoutUserInfo userInfo : getLogoutUserInfos()) {
			if (userInfo.equals((UserInfo)newUser)) {
				oldUser = userInfo;
			}
		}
		 
		// if has remove the user from user info and sent stored user activities to this client
		if (oldUser != null) {
			getLogoutUserInfos().remove(oldUser);
			for (String message : oldUser.getMessageArray()) {
				con.writeMsg(message);
			}
			
			// Broadcast deleting logout user info
			JSONObject msgObj = new JSONObject();
			msgObj.put("command", "DELETE_LOGOUT_USER");
			Gson gUserInfo = new Gson();
			msgObj.put("userInfo", gUserInfo.toJson(oldUser));

			Control.getInstance().broadcastMessage(null, msgObj.toJSONString(), true);
		}
	}
	
	/**
	 * add by yicongLI check if can operate authentication
	 * @param username the username from client
	 * @param secret   the secret from client
	 * @param con	   the connection object
	 * @return -1: shouldn't authenticate user; other number: latest message index of current user
	 */
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
	
	/*
	 * check if need to store message for the log out user 
	 * Description: 
	 * after the client log out, the client could log in to any server in the system again
	 * so every server should store the log out user info, 
	 * when one server finish sending the message to the client, shoud broadcast to the others to 
	 * delete the message cache
	 */
	@SuppressWarnings("unchecked")
	public void checkIfStoreMessagesForLogoutUser (long time, String jsonStr) {
		for (LogoutUserInfo userInfo : getLogoutUserInfos()) {
			
			if (userInfo.getLastLogoutTime() < time) {
				userInfo.getMessageArray().add(jsonStr);
				
				JSONObject msgObjFinal = new JSONObject();
				msgObjFinal.put("command", "LOGOUT_USER_MESSAGE");
				Gson gUserInfo = new Gson();
				msgObjFinal.put("userinfo", gUserInfo.toJson(userInfo));
				
				// Broadcasting message to all the other servers,except anonymous
				if (!userInfo.isAnonymous()) {
					Control.getInstance().broadcastMessage(null, msgObjFinal.toJSONString(), true);
				}
			}
		}
	}
	
	/*
	 * 	when receive the logout user info, check the local storage if has same user info
	 *  Has: then add message to local storage and sort the message by time stamp
	 *  Hasn't: add user to the local storage directly
	 */
	public synchronized void recieveLogoutUserInfo(LogoutUserInfo userInfo) {
		Boolean hasSameLogoutUser = false;
		for (LogoutUserInfo logoutUserInfo : getLogoutUserInfos()) {
			if (logoutUserInfo.equals(userInfo)) {
				hasSameLogoutUser = true;
				// add all coming message into array
				logoutUserInfo.getMessageArray().addAll(userInfo.getMessageArray());
				// remove redundant data
				Set<String> set = new HashSet<String>();
				set.addAll(logoutUserInfo.getMessageArray());
				
				ArrayList<String> newMessageArray = new ArrayList<String>(set);
				logoutUserInfo.setMessageArray(newMessageArray);
				
				// sort with time stamp
				Comparator<String> compare = new Comparator<String>() {
					@Override  
		            public int compare(String o1, String o2) {  
		                JSONObject object1 = null;
		                JSONObject object2 = null;
						try {
							object1 = (JSONObject)Control.parser.parse(o1);
							object2 = (JSONObject)Control.parser.parse(o2);
						} catch (ParseException e) {
							e.printStackTrace();
						}

		                if((Long)object1.get("timestamp") > (Long)object2.get("timestamp")) { 
		                    return 1;  
		                } else {
		                	return -1;  
		                }
		            }
				};
				
				logoutUserInfo.getMessageArray().sort(compare);
			}
		}
		
		// if don't have same logout user, then save the user info directly
		if (!hasSameLogoutUser) {
			getLogoutUserInfos().add(userInfo);
		}
	}
}
