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
	private ArrayList<IncomeActicityUserInfo> incomeActicityInfos = null;
	
	public UserManager() {
		setLogoutUserInfos(new ArrayList<LogoutUserInfo>());
		setLoginUserInfos(new ArrayList<LoginUserInfo>());
		setIncomeActicityInfos(new ArrayList<IncomeActicityUserInfo>());
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
	
	public ArrayList<IncomeActicityUserInfo> getIncomeActicityInfos() {
		return incomeActicityInfos;
	}

	public void setIncomeActicityInfos(ArrayList<IncomeActicityUserInfo> incomeMessageInfos) {
		this.incomeActicityInfos = incomeMessageInfos;
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
			
			String msgStr = msgObj.toJSONString();
			Control.log.debug(msgStr);
			Control.getInstance().broadcastMessage(null, msgStr, true);
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
				logoutUserInfos.add(new LogoutUserInfo(userInfo.getUsername(), userInfo.getSecret(), userInfo.getIpAddress()));
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
	public void checkIfStoreMessagesForLogoutUser (ArrayList<String> jsonStrArray) {
		for (String jsonStr : jsonStrArray) {
			// check invalids
			JSONObject msgObject = null;
			try {
				msgObject = (JSONObject) Control.parser.parse(jsonStr);
			} catch (Exception e) {
				
				e.printStackTrace();
			}
			
			long time = ((Long) msgObject.get("timestamp")).longValue();
			
			for (LogoutUserInfo userInfo : getLogoutUserInfos()) {
				
				if (userInfo.getLastLogoutTime() < time) {
					userInfo.getMessageArray().add(jsonStr);
				}
			}
		}
		
		for (LogoutUserInfo userInfo : getLogoutUserInfos()) {
			if (userInfo.isNeedToSynchronize()) {
				JSONObject msgObjFinal = new JSONObject();
				msgObjFinal.put("command", "LOGOUT_USER_MESSAGE");
				Gson gUserInfo = new Gson();
				msgObjFinal.put("userinfo", gUserInfo.toJson(userInfo));
				
				// Broadcasting message to all the other servers,except anonymous
				String broadcastStr = msgObjFinal.toJSONString();
				if (!userInfo.isAnonymous()) {
					Control.getInstance().broadcastMessage(null, msgObjFinal.toJSONString(), true);
				}
				
				Control.log.debug("Store message:" + broadcastStr);
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
				sortActivityMessageArray(logoutUserInfo.getMessageArray());
			}
		}
		
		// if don't have same logout user, then save the user info directly
		if (!hasSameLogoutUser) {
			userInfo.setLogoutFromCurrentServer(false);
			getLogoutUserInfos().add(userInfo);
		}
	}
	
	private void sortActivityMessageArray(ArrayList<String> messageArray) {
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
		
		messageArray.sort(compare);
	}
	
	/**
	 * check the index of current activities, if its not the latest activity, then wait for the missing message
	 * @param msgObject the activity message
	 * @param sender_connection the connection of sender
	 * @return true: broadcast to all client; false: prevent broadcast to clients
	 */
	public boolean checkIfBroadcastToClients(JSONObject msgObject, Connection sender_connection) {
		IncomeActicityUserInfo userInfo = null;
		JSONObject actobj = (JSONObject) msgObject.get("activity");
		String name = (String) actobj.get("authenticated_user");
		String ip   = (String) msgObject.get("ip");
		for (IncomeActicityUserInfo incomeActicityUserInfo : incomeActicityInfos) {
			if (incomeActicityUserInfo.isCurrentInfo(name, ip)) {
				userInfo = incomeActicityUserInfo;
			}
		}
		
		// if did not find the user info, then create a new one
		boolean newUserInfo = userInfo == null;
		if (userInfo == null) {
			userInfo = new IncomeActicityUserInfo(name, sender_connection);
			incomeActicityInfos.add(userInfo);
		}
		
		int curIndex = ((Integer) msgObject.get("index")).intValue();
		int latestIndex = userInfo.getLatestIndex();
		
		// if current activity the next one of the recorded latest activity, 
		// or didn't have this user info before, then broadcast directly
		if (curIndex == latestIndex + 1 || newUserInfo) {
			userInfo.setLatestIndex(curIndex);
			return true;
		}
		
		// TODO: how to handle the situation that the server join the system just in the unstable situation?
		// which may cause that the latestIndex initialised with a message which is not the latest one
		if (curIndex < latestIndex) {
			// if current index less than latestIndex, means currently missing message
			userInfo.getMessageArray().add(msgObject.toJSONString());
			// 
			userInfo.setFirstIndex(Math.min(curIndex, userInfo.getFirstIndex()));
			sortActivityMessageArray(userInfo.getMessageArray());
			
			// when get all user info, broaddcast to the connected client
			// TODO handle the situation that 
			if (userInfo.getLatestIndex() - userInfo.getFirstIndex() == userInfo.getMessageArray().size() - 1) {
				for (String activityMsg : userInfo.getMessageArray()) {
					broadcastMessageToClient(activityMsg);
				}
				
				// if any the client has logout during this process, server should store the message for them
				// and send these message when the client re-login
				checkIfStoreMessagesForLogoutUser(userInfo.getMessageArray());
				userInfo.resetInfo();
			}
			
		} else if (curIndex == latestIndex) {
			// if the index is similar to the latest index, then ignore the message
		} else if (curIndex > latestIndex + 1) {
			// if the index is larger than the latest index+1, then store the message and wait for missing message
			userInfo.getMessageArray().add(msgObject.toJSONString());
			userInfo.setLatestIndex(curIndex);
			userInfo.setFirstIndex(latestIndex);
		} 
		
		return false;
	}
	
	/**
	 * BROADCAST message to client
	 * Send the message to all connected client
	 */
	public synchronized void broadcastMessageToClient(String message) {
		for (Connection receiver_connection : Control.connections) {

			// only broadcast to client
			if (receiver_connection.getIsServer()) {
				continue;
			}

			receiver_connection.writeMsg(message);
		}
	}
}
