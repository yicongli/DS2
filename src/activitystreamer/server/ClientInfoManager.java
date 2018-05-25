package activitystreamer.server;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.google.gson.Gson;

public class ClientInfoManager {
	
	private ArrayList<LogoutClientInfo> logoutClientInfos = null;	// the client that has logged out from system
	private ArrayList<LoginClientInfo> loginClientnfos = null;		// the client that currently login the server
	private ArrayList<IncomeActicityClientInfo> incomeActicityInfos = null; // the activity info that current server received 
	
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
	 * Add the login user information to the storage and 
	 * check if need to send cache activities to the client
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
			
			// Broadcast deleting logout user info message to all the other servers
			JSONObject msgObj = new JSONObject();
			msgObj.put("command", "DELETE_LOGOUT_USER");
			Gson gUserInfo = new Gson();
			msgObj.put("userInfo", gUserInfo.toJson(oldClient));
			
			String msgStr = msgObj.toJSONString();
			Control.getInstance().broadcastMessage(null, msgStr, true);
			
			Control.log.debug(msgStr);
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
	
	/**
	 * remove login user when connection lost or receive logout message
	 * @param con the connection with client
	 * @return if removed the login client info from manager
	 */
	public synchronized boolean removeLoginClientInfo (Connection con) {
		if (con.getIsServer()) {
			return false;
		}
		
		LoginClientInfo curClientInfo = null;
		for (LoginClientInfo clientInfo : getLoginClientInfos()) {
			if (clientInfo.getConnection().equals(con)) {
				curClientInfo = clientInfo;
				LogoutClientInfo info = new LogoutClientInfo(clientInfo.getUsername(), clientInfo.getIpAddress());
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
	 * when one server finish sending the message to the client, should broadcast to the others to 
	 * delete the message cache
	 */
	@SuppressWarnings("unchecked")
	public void checkIfStoreMessagesForLogoutClient (ArrayList<String> jsonStrArray) {
		for (String jsonStr : jsonStrArray) {
			// parse the message 
			JSONObject msgObject = null;
			try {
				msgObject = (JSONObject) Control.parser.parse(jsonStr);
			} catch (Exception e) {
				
				e.printStackTrace();
			}
			
			// get the time stamp of message
			long time = ((Long) msgObject.get("timestamp")).longValue();
			// if any client logout before the message sent, and logout from current server 
			// then add the message to the cache
			for (LogoutClientInfo clientInfo : getLogoutClientInfos()) {
				if (clientInfo.getLastLogoutTime() < time 
						&& clientInfo.isLogoutFromCurrentServer()) {
					clientInfo.getMessageArray().add(jsonStr);
				}
			}
		}
		
		// if the client logout from current server, then broadcast logout info to all the other server
		for (LogoutClientInfo clientInfo : getLogoutClientInfos()) {
			if (clientInfo.isLogoutFromCurrentServer()) {
				JSONObject msgObjFinal = new JSONObject();
				msgObjFinal.put("command", "LOGOUT_USER_MESSAGE");
				Gson gClientInfo = new Gson();
				msgObjFinal.put("userinfo", gClientInfo.toJson(clientInfo));
				
				// Broadcasting message to all the other servers, except anonymous
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
		Boolean findLogoutClient = false;
		for (LogoutClientInfo logoutClientInfo : getLogoutClientInfos()) {
			if (logoutClientInfo.equals(clientInfo)) {
				findLogoutClient = true;
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
		if (!findLogoutClient) {
			clientInfo.setLogoutFromCurrentServer(false);
			getLogoutClientInfos().add(clientInfo);
		}
	}
	
	/**
	 * Sort activity message array with time stamp
	 * @param messageArray
	 */
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
				// compare with timestamp
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
	@SuppressWarnings("unchecked")
	public boolean checkIfBroadcastToClients(JSONObject msgObject, Connection sender_connection) {
		IncomeActicityClientInfo clientInfo = null;
		
		JSONObject actobj = (JSONObject) msgObject.get("activity");
		String name = (String) actobj.get("authenticated_user");
		String ip   = (String) msgObject.get("ip");
		// check if local has income activity info
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
		
		if (curIndex >= clientInfo.getFirstIndex()) {
			// if current index less than first, means currently missing message
			clientInfo.getMessageArray().add(msgObject.toJSONString());
			sortActivityMessageArray(clientInfo.getMessageArray());
			
			// when get all user info, broadcast to the connected client
			if (clientInfo.getLatestIndex() - clientInfo.getFirstIndex() == clientInfo.getMessageArray().size() - 1) {
				for (String activityMsg : clientInfo.getMessageArray()) {
					broadcastMessageToClient(activityMsg);
				}
				
				// if any the client has logout during this process, server should store the message for them
				// and send these message when the client re-login
				checkIfStoreMessagesForLogoutClient(clientInfo.getMessageArray());
				// reset the message cache
				clientInfo.resetInfo();
			}
			
		} else if (curIndex > latestIndex + 1) {
			// if the index is larger than the latest index+1, then store the message and wait for missing message
			clientInfo.getMessageArray().add(msgObject.toJSONString());
			clientInfo.setLatestIndex(curIndex);
			clientInfo.setFirstIndex(latestIndex);
			// request lost user info
			JSONObject msgObjFinal = new JSONObject();
			msgObjFinal.put("command", "LOST_MESSAGE_REQUEST");
			msgObjFinal.put("username", name);
			msgObjFinal.put("ip", ip);
			
			ArrayList<Long> indexArr = new ArrayList<Long>();
			Gson gIndexArray = new Gson();
			for (int i = clientInfo.getFirstIndex() + 1; i < clientInfo.getLatestIndex(); i++) {
				indexArr.add(new Long(i));
			}
			
			msgObjFinal.put("index", gIndexArray.toJson(indexArr));
			sender_connection.writeMsg(msgObjFinal.toJSONString());
		} 
		
		// if the index is same to the latest index, then ignore the message
		// if the index is less than latest index, then it's not below to current request, then ignore the message
		
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
	
	/**
	 * if any currently login clients has different secret compared with new income ones, then logout the user
	 * @param clientsInfo
	 */
	public void checkLoggingoutClients (JSONObject clientsInfo) {
		for (Object key : clientsInfo.keySet()) {
			String username = (String) key;
			JSONObject infoObj = (JSONObject)clientsInfo.get(key);
			String secret   = (String)infoObj.get("password");
			
			ArrayList<LoginClientInfo> logoutClientInfos = new ArrayList<LoginClientInfo>();
			for (LoginClientInfo localCLientInfo : getLoginClientInfos()) {
				if (localCLientInfo.getUsername().equals(username)
						&& !localCLientInfo.getSecret().equals(secret)) {
					
					Control.getInstance().connectionClosed(localCLientInfo.getConnection());
					logoutClientInfos.add(localCLientInfo);
				}
			}
			
			getLoginClientInfos().removeAll(logoutClientInfos);
		}
	}
}
