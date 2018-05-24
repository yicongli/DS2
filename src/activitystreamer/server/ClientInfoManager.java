package activitystreamer.server;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;

public class ClientInfoManager {
	
	private ArrayList<LogoutClientInfo> logoutClientInfos = null;
	private ArrayList<LoginClientInfo> loginClientnfos = null;
	private ArrayList<IncomeActicityClientInfo> incomeActicityInfos = null;
	
	public ClientInfoManager() {
		setLogoutClientInfos(new ArrayList<LogoutClientInfo>());
		setLoginClientInfos(new ArrayList<LoginClientInfo>());
		setIncomeActicityInfos(new ArrayList<IncomeActicityClientInfo>());
	}
	
	public synchronized ArrayList<LoginClientInfo> getLoginClientInfos() {
		return loginClientnfos;
	}

	public synchronized void setLoginClientInfos(ArrayList<LoginClientInfo> loginUserInfos) {
		this.loginClientnfos = loginUserInfos;
	}

	public synchronized ArrayList<LogoutClientInfo> getLogoutClientInfos() {
		return logoutClientInfos;
	}

	public synchronized void setLogoutClientInfos(ArrayList<LogoutClientInfo> logoutUserInfos) {
		this.logoutClientInfos = logoutUserInfos;
	}
	
	public ArrayList<IncomeActicityClientInfo> getIncomeActicityInfos() {
		return incomeActicityInfos;
	}

	public void setIncomeActicityInfos(ArrayList<IncomeActicityClientInfo> incomeMessageInfos) {
		this.incomeActicityInfos = incomeMessageInfos;
	}
	
	/**
	 * Add the login user information to the storage and check if need to send cache activities to the client
	 * @param username 	current user's username
	 * @param secret	current user's secret
	 * @param con		current user's con
	 */
	@SuppressWarnings("unchecked")
	public synchronized void addNewLoginClientInfo(String username, String secret, Connection con) {
		LoginClientInfo newUser = new LoginClientInfo(username, secret, con);
		getLoginClientInfos().add(newUser);
		
		// check if local has stored logout user information
		LogoutClientInfo oldClient = null;
		for (LogoutClientInfo userInfo : getLogoutClientInfos()) {
			if (userInfo.equals((ClientInfo)newUser)) {
				oldClient = userInfo;
			}
		}
		 
		// if has remove the user from user info and sent stored user activities to this client
		if (oldClient != null) {
			getLogoutClientInfos().remove(oldClient);
			for (String message : oldClient.getMessageArray()) {
				con.writeMsg(message);
			}
			
			// Broadcast deleting logout user info
			JSONObject msgObj = new JSONObject();
			msgObj.put("command", "DELETE_LOGOUT_USER");
			Gson gUserInfo = new Gson();
			msgObj.put("userInfo", gUserInfo.toJson(oldClient));
			
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
	public synchronized int shouldAuthenticateClient (String username, String secret, Connection con) {
		int latestIndex = -1;
		for (LoginClientInfo clientInfo : getLoginClientInfos()) {
			if (clientInfo.getUsername().equals(username) 
					&& clientInfo.getSecret().equals(secret)
						&& clientInfo.getConnection().equals(con)) {
				latestIndex = clientInfo.increaseIndex();
			}
		}
		
		return latestIndex;
	}
	
	/*
	 * remove login user when connection lost or receive logout message
	 */
	public synchronized boolean removeLoginClientInfo (Connection con) {
		if (con.getIsServer()) {
			return false;
		}
		
		LoginClientInfo curClientInfo = null;
		for (LoginClientInfo clientInfo : getLoginClientInfos()) {
			if (clientInfo.getConnection().equals(con)) {
				curClientInfo = clientInfo;
				LogoutClientInfo info = new LogoutClientInfo(clientInfo.getUsername(), 
																clientInfo.getSecret(), 
																clientInfo.getIpAddress());
				logoutClientInfos.add(info);
			}
		}
		
		if (curClientInfo != null) {
			getLoginClientInfos().remove(curClientInfo);
		}
		
		return curClientInfo != null;
	}
	
	/*
	 * check if need to store message for the log out client 
	 * Description: 
	 * after the client log out, the client could log in to any server in the system again
	 * so every server should store the log out user info, 
	 * when one server finish sending the message to the client, shoud broadcast to the others to 
	 * delete the message cache
	 */
	@SuppressWarnings("unchecked")
	public void checkIfStoreMessagesForLogoutClient (ArrayList<String> jsonStrArray) {
		for (String jsonStr : jsonStrArray) {
			// check invalids
			JSONObject msgObject = null;
			try {
				msgObject = (JSONObject) Control.parser.parse(jsonStr);
			} catch (Exception e) {
				
				e.printStackTrace();
			}
			
			long time = ((Long) msgObject.get("timestamp")).longValue();
			
			for (LogoutClientInfo clientInfo : getLogoutClientInfos()) {
				
				if (clientInfo.getLastLogoutTime() < time) {
					clientInfo.getMessageArray().add(jsonStr);
				}
			}
		}
		
		for (LogoutClientInfo clientInfo : getLogoutClientInfos()) {
			if (clientInfo.isNeedToSynchronize()) {
				JSONObject msgObjFinal = new JSONObject();
				msgObjFinal.put("command", "LOGOUT_USER_MESSAGE");
				Gson gClientInfo = new Gson();
				msgObjFinal.put("userinfo", gClientInfo.toJson(clientInfo));
				
				// Broadcasting message to all the other servers,except anonymous
				String broadcastStr = msgObjFinal.toJSONString();
				if (!clientInfo.isAnonymous()) {
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
	public synchronized void recieveLogoutClientInfo(LogoutClientInfo clientInfo) {
		Boolean hasSameLogoutClient = false;
		for (LogoutClientInfo logoutClientInfo : getLogoutClientInfos()) {
			if (logoutClientInfo.equals(clientInfo)) {
				hasSameLogoutClient = true;
				// add all coming message into array
				logoutClientInfo.getMessageArray().addAll(clientInfo.getMessageArray());
				// remove redundant data
				Set<String> set = new HashSet<String>();
				set.addAll(logoutClientInfo.getMessageArray());
				
				ArrayList<String> newMessageArray = new ArrayList<String>(set);
				logoutClientInfo.setMessageArray(newMessageArray);
				
				// sort with time stamp
				sortActivityMessageArray(logoutClientInfo.getMessageArray());
			}
		}
		
		// if don't have same logout user, then save the user info directly
		if (!hasSameLogoutClient) {
			clientInfo.setLogoutFromCurrentServer(false);
			getLogoutClientInfos().add(clientInfo);
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
		IncomeActicityClientInfo clientInfo = null;
		JSONObject actobj = (JSONObject) msgObject.get("activity");
		String name = (String) actobj.get("authenticated_user");
		String ip   = (String) msgObject.get("ip");
		for (IncomeActicityClientInfo incomeActicityClientInfo : incomeActicityInfos) {
			if (incomeActicityClientInfo.isCurrentInfo(name, ip)) {
				clientInfo = incomeActicityClientInfo;
			}
		}
		
		// if did not find the user info, then create a new one
		boolean createNewClientInfo = clientInfo == null;
		if (clientInfo == null) {
			clientInfo = new IncomeActicityClientInfo(name, sender_connection);
			incomeActicityInfos.add(clientInfo);
		}
		
		int curIndex = ((Integer) msgObject.get("index")).intValue();
		int latestIndex = clientInfo.getLatestIndex();
		
		// if current activity the next one of the recorded latest activity, 
		// or didn't have this user info before, then broadcast directly
		if (curIndex == latestIndex + 1 || createNewClientInfo) {
			clientInfo.setLatestIndex(curIndex);
			return true;
		}
		
		// TODO: how to handle the situation that the server join the system just in the unstable situation?
		// which may cause that the latestIndex initialised with a message which is not the latest one
		if (curIndex < latestIndex) {
			// if current index less than latestIndex, means currently missing message
			clientInfo.getMessageArray().add(msgObject.toJSONString());
			// 
			clientInfo.setFirstIndex(Math.min(curIndex, clientInfo.getFirstIndex()));
			sortActivityMessageArray(clientInfo.getMessageArray());
			
			// when get all user info, broaddcast to the connected client
			// TODO handle the situation that 
			if (clientInfo.getLatestIndex() - clientInfo.getFirstIndex() == clientInfo.getMessageArray().size() - 1) {
				for (String activityMsg : clientInfo.getMessageArray()) {
					broadcastMessageToClient(activityMsg);
				}
				
				// if any the client has logout during this process, server should store the message for them
				// and send these message when the client re-login
				checkIfStoreMessagesForLogoutClient(clientInfo.getMessageArray());
				clientInfo.resetInfo();
			}
			
		} else if (curIndex == latestIndex) {
			// if the index is similar to the latest index, then ignore the message
		} else if (curIndex > latestIndex + 1) {
			// if the index is larger than the latest index+1, then store the message and wait for missing message
			clientInfo.getMessageArray().add(msgObject.toJSONString());
			clientInfo.setLatestIndex(curIndex);
			clientInfo.setFirstIndex(latestIndex);
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
