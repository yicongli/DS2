package activitystreamer.server;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.Timer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import activitystreamer.util.Settings;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static boolean term = false;
	private static Listener listener;
	private static ArrayList<JSONObject> announcementInfo; 	// the announcement Info from the other server
	private static String uniqueID; 						// the Identification of current server
	private static ArrayList<LockItem> lockItemArray;  		// the items handling the lock request
	public static JSONParser parser; 						// the parser to parse the JSON data
	public static UserManager userManager = null; 			// UserInfo manager
	private static int reconnectTime = 0;					// Reconnect time
	private static Timer reconnectTimer = null;				// Reconnect timer

	protected static Control control = null;

	public static Control getInstance() {
		if (control == null) {
			control = new Control();
		}
		return control;
	}

	public Control() {
		
		connections 	  = new ArrayList<Connection>();	// initialize the connections item array
		announcementInfo  = new ArrayList<JSONObject>(); 	// initialize the announcement item array
		lockItemArray 	  = new ArrayList<LockItem>();		// initialize the lock item array
		userManager 	  = new UserManager();				// manage login/logout user info
		parser 			  = new JSONParser(); 				// initialize parser and remote connection
		uniqueID 		  = Settings.nextSecret();
		
		try {
			listener = new Listener(); 	// start a listener
			initiateConnection();		// initiate connection with remote server
			start(); 					// start regular operation
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: " + e1);
			System.exit(-1);
		}
	}

	public void initiateConnection() {
		// make a connection to another server if remote hostname is supplied
		if (Settings.getRemoteHostname() != null) {
			try {
				Socket newSocket = new Socket();
				InetSocketAddress address = new InetSocketAddress(Settings.getRemoteHostname(), Settings.getRemotePort());
				// connect time out is shorter than reconnect interval
				newSocket.connect(address, 5000);
				Connection newCon = outgoingConnection(newSocket);
				
				newCon.setIsServer(true);		// set flag of server
				newCon.setIsRemoteServer(true); // set if this server is the remote server
				authenticateRequest(newCon);	// send authentication to the parent server
				resetReconnectOperation();
			} catch (IOException e) {
				log.error("failed to make connection to " 
							+ Settings.getRemoteHostname() + ":"
							+ Settings.getRemotePort() + " :" + e);
			}
		}
	}

	/*
	 * send authenticate include: S-S 
	 * @secret: the secret of server system
	 * @port: the listening port of current server
	 */
	@SuppressWarnings("unchecked")
	private synchronized void authenticateRequest(Connection outCon) {
		JSONObject msgObj = new JSONObject();
		msgObj.put("command", "AUTHENTICATE");
		msgObj.put("secret", Settings.getServerSecret());
		msgObj.put("port", Settings.getLocalPort());
		outCon.writeMsg(msgObj.toJSONString());
	}

	/*
	 * add by yicongLI 23-04-18 send lock request when register a new account
	 */
	@SuppressWarnings("unchecked")
	private synchronized void lockRequest(String userName, String secret, Connection clientCon) {
		JSONObject msgObj = new JSONObject();
		msgObj.put("command", "LOCK_REQUEST");
		msgObj.put("username", userName);
		msgObj.put("secret", secret);

		Integer outNum = broadcastMessage(null, msgObj.toJSONString(), true);
		
		// if current server has no connected server, then check local storage directly.
		if (outNum == 0) {
			if (FileOperator.checkLocalStorage(userName) != null) {
				registerFail(userName, clientCon);
			} else {
				registerSuccess(userName, secret, clientCon);
			}
		}
		else {
			lockItemArray.add(new LockItem(userName, clientCon, outNum));
		}
	}

	/*
	 * add by yicongLI 20-04-18 broadcast announcement or activities
	 * 
	 * @param con: the connection from which receive the message, if null, then
	 * broadcast to all the other connections
	 * 
	 * @param msg: broadcast message
	 * 
	 * @param onlySever: if ture, then just broadcast to the other servers.
	 */
	private synchronized Integer broadcastMessage(Connection con, String msg, boolean onlySever) {
		Integer broadcastTime = 0;
		for (Connection broadcastCon : connections) {

			// when server to server only, if the connection is client,
			// then ignore it and check next one
			if (onlySever && !broadcastCon.getIsServer()) {
				continue;
			}

			// if con is equal to null, then broad cast to all connection
			if (con == null) {
				broadcastCon.writeMsg(msg);
				broadcastTime++;
				continue;
			}

			if (con != broadcastCon) {
				broadcastCon.writeMsg(msg);
				broadcastTime++;
			}
		}

		return broadcastTime;
	}
	
	/*
	 * Processing incoming messages from the connection. Return true if the connection should close.
	 * mod by yicongLI 19-04-18 add json parsing operation
	 */
	public synchronized boolean process(Connection con, String msg) {
		log.debug(msg);

		String command = "";
		JSONObject msgObject;
		try {
			msgObject = (JSONObject) parser.parse(msg);
			command = (String) msgObject.get("command");

			if (command == null) {
				command = "";
			}
		} catch (Exception e) {
			e.printStackTrace();
			responseInvalidMsg("Message parse error", con);
			return true;
		}

		switch (command) {
		case "AUTHENTICATE":
			return authentication(con, msgObject);
		case "INVALID_MESSAGE":
			log.info("Invalid message return:" + (String) msgObject.get("info"));
			return true;
		case "AUTHENTICATION_FAIL":
			log.info("Authentication fail return:" + (String) msgObject.get("info"));
			return true;
		case "LOGIN":
			log.info("Login Method initiated for username :" + (String) msgObject.get("username"));
			return loginUser(con, msgObject);
		case "LOGOUT":
			log.info("Log out");
			return true;
		case "ACTIVITY_MESSAGE":
			return activityMessage(con, msg);
		case "ACTIVITY_BROADCAST":
			return broadcastActivities(con, msg);
		case "SERVER_ANNOUNCE":
			return receiveAnnouncement(con, msgObject);
		case "REGISTER":
			return registerUser(con, msgObject);
		case "LOCK_REQUEST":
			return receiveLockRequest(con, msgObject);
		case "LOCK_DENIED":
			return receiveLockReply(con, msgObject, true);
		case "LOCK_ALLOWED":
			return receiveLockReply(con, msgObject, false);
		case "USERINFOREPLY":
			return saveUserInfo(msgObject);
		default:
			responseInvalidMsg("command is not exist", con);
			return true;
		}
	}

	/*
	 * received message response functions
	 */

	/*
	 * add by yicongLI 19-04-18 return invalid message info and close connection
	 */
	private synchronized void responseInvalidMsg(String info, Connection con) {
		responseMsg("INVALID_MESSAGE", info, con);
		connectionClosed(con);
	}

	// thaol4
	// return the response with format {"command":"","info":""}
	@SuppressWarnings("unchecked")
	private void responseMsg(String cmd, String info, Connection con) {
		JSONObject msgObj = new JSONObject();
		msgObj.put("command", cmd);
		msgObj.put("info", info);
		con.writeMsg(msgObj.toJSONString());
		
		log.info(msgObj.toJSONString());
	}
	
	/*
	 * Added by shajidm@student.unimelb.edu.au to define the LogIn Method
	 */
	private synchronized boolean loginUser(Connection con, JSONObject loginObject) {
		String cmd = null;
		String info = null;
		
		// check if the login info is valid
		if (!loginObject.containsKey("username")) {
			responseInvalidMsg("The instruction misses a username", con);
			return true;
		} 
		
		String username = (String) loginObject.get("username");
		String secret = "";
		if (!username.equalsIgnoreCase("anonymous")) {
			secret = (String) loginObject.get("secret");
		}

		// Search for the username
		String localsecret = FileOperator.checkLocalStorage(username);
		if (localsecret != null && localsecret.equals(secret)) {
			cmd = "LOGIN_SUCCESS";
			info = "logged in as user " + username;;
			userManager.addNewLoginUserInfo(username, secret, con);
			responseMsg(cmd, info, con);
			
			// check if need to redirect login, if need, then close current server
			return redirectionLogin(con);
		} else {
			return loginFailed("User"+username+" not found or secret: "+secret+" not right", con);
		}
	}
	
	/*
	 * return login fail msg 
	 */
    private boolean loginFailed(String info, Connection con) {
		responseMsg("LOGIN_FAILED", info, con);
		connectionClosed(con);
		return true;
	}
    
	/*
	 * to define the redirectionLogin
	 */
	@SuppressWarnings("unchecked")
	private boolean redirectionLogin(Connection con) {
		// get current server load 
		JSONObject target = getRedirectionInfo();
		if (target == null) {
			return false;
		} 
		
		String newHostName = (String) target.get("hostname");
		Long newPort = (Long) target.get("port");
		
		JSONObject msgObj = new JSONObject();
		msgObj.put("command", "REDIRECT");
		msgObj.put("hostname", newHostName);
		msgObj.put("port", newPort);
		con.writeMsg(msgObj.toJSONString());
		
		return true;
	}
	
	/*
	 *  get server info with lowest load
	 */
	private JSONObject getRedirectionInfo() {
		int currentLoad = clientLoadNum();
		JSONObject target = null;
		
		for (JSONObject jsonAvailabilityObj : announcementInfo) {
			Long newLoad = (Long) jsonAvailabilityObj.get("load");
			if (newLoad < currentLoad - 1) {
				// get lowest load server
				currentLoad = newLoad.intValue();
				target = jsonAvailabilityObj;
			}
		}
		
		return target;
	}

	/*
	 * add by yicongLI 18-04-19 check authentication requested from another server
	 */
	private synchronized boolean authentication(Connection con, JSONObject authObj) {
		if (!authObj.containsKey("secret")) {
			responseInvalidMsg("authentication invalid: no secret", con);
			return true;
		}

		String secret = (String) authObj.get("secret");
		if (!secret.equals(Settings.getServerSecret())) {
			String info = "the supplied secret is incorrect: " + secret;
			responseMsg("AUTHENTICATION_FAIL", info, con);
			connectionClosed(con);
			return true;
		}
		
		// No reply if the authentication succeeded
		con.setIsServer(true);
		con.setSecret(Settings.getServerSecret());
		
		userInfoReply(con);
		return false;
	}
	
	/*
	 * send info back to the server which request authentication include : S-S 
	 */
	@SuppressWarnings("unchecked")
	private synchronized void userInfoReply(Connection outCon) {
		JSONObject msgObj = new JSONObject();
		msgObj.put("command", "USERINFOREPLY");
		msgObj.put("remotehostname", Settings.getRemoteHostname());
		msgObj.put("remoteport", Settings.getRemotePort());
		msgObj.put("userinfo", FileOperator.allUserInfo().toJSONString());
		
		// synchronize logout user info 
		Gson logoutUser = new Gson();
		String logoutInfo = logoutUser.toJson(userManager.getLogoutUserInfos());
		msgObj.put("logoutUserInfos", logoutInfo);
		
		outCon.writeMsg(msgObj.toJSONString());
	}

	/*
	 * Added by thaol4
	 * register fail, server replies, close connection
	 */
	private void registerFail(String username, Connection con) {
		String cmd = "REGISTER_FAILED";
		String info = username + " is already registered with the system";
		responseMsg(cmd, info, con);
		connectionClosed(con);
	}

	/*
	 * Added by thaol4
	 * register success, append new username and secret pair to file, server replies
	 */
	private void registerSuccess(String username, String secret, Connection con) {
		String cmd = "REGISTER_SUCCESS";
		String info = "register success for " + username;
		responseMsg(cmd, info, con);
		
		FileOperator.saveUserName(username, secret);
		
		//After register successfully, login
		String loginMsg = "{\"command\":\"LOGIN\",\"username\":\"" + username
				+ "\",\"secret\" :\"" + secret + "\"}";
		try {
			JSONObject msgObject = (JSONObject) parser.parse(loginMsg);
			loginUser(con, msgObject);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// Added by thaol4
	private synchronized boolean registerUser(Connection con, JSONObject regObj) {
		// The msg is invalid
		if (!regObj.containsKey("username")) {
			responseInvalidMsg("the message must contain non-null key username", con);
			return true;
		}

		if (!regObj.containsKey("secret")) {
			responseInvalidMsg("the message must contain non-null key secret", con);
			return true;
		}

		String username = (String) regObj.get("username");
		String secret = (String) regObj.get("secret");

		// file is already existed
		if (FileOperator.checkLocalStorage(username) != null) {
			registerFail(username, con);
			return true;
		} 
		else {
			//Send lock request by invoking lockRequest function
			lockRequest(username, secret, con);
			return false;
		}
	}

	/*
	 * add by yicongLI 23-04-18 handling the operation when receive lock_request
	 * modified by thaol4
	 */
	@SuppressWarnings("unchecked")
	private synchronized boolean receiveLockRequest(Connection con, JSONObject msgObj) {
		if (!con.getIsServer()) {
			responseInvalidMsg("Message received from an unauthenticated server", con);
			return true;
		} else if (!msgObj.containsKey("username")) {
			responseInvalidMsg("Message does not contain the id field", con);
			return true;
		} else if (!msgObj.containsKey("secret")) {
			responseInvalidMsg("Message does not contain the hostname field", con);
			return true;
		}

		String username = (String) msgObj.get("username");
		String secret = (String) msgObj.get("secret");
		
		if (FileOperator.checkLocalStorage(username) != null) {
			// if found name in local storage, then reply the deny message
			msgObj.put("command", "LOCK_DENIED");
			con.writeMsg(msgObj.toJSONString());
			
			log.info(msgObj.toJSONString());
		} else {
			FileOperator.saveUserName(username, secret);
			
			// if this server is the end of the tree, then reply directly
			Integer outNum = broadcastMessage(con, msgObj.toJSONString(), true);
			if (outNum == 0) {
				msgObj.put("command", "LOCK_ALLOWED");
				con.writeMsg(msgObj.toJSONString());
				log.info(msgObj.toJSONString());
			} else {
				// add record of this lock request 
				lockItemArray.add(new LockItem((String) msgObj.get("username"), con, outNum));
			}
		}
		
		return false;
	}

	/*
	 * add by yicongLI 23-04-18 handling the operation when receive lock_deny or
	 * lock_allow from another server
	 */
	// modified by thaol4
	private synchronized boolean receiveLockReply(Connection con, JSONObject msgObj, boolean isDeny) {
		if (!con.getIsServer()) {
			responseInvalidMsg("Message received from an unauthenticated server", con);
			return true;
		} else if (!msgObj.containsKey("username")) {
			responseInvalidMsg("Message does not contain the id field", con);
			return true;
		} else if (!msgObj.containsKey("secret")) {
			responseInvalidMsg("Message does not contain the hostname field", con);
			return true;
		}

		String userName = (String) msgObj.get("username");
		String secret = (String) msgObj.get("secret");

		Predicate<? super LockItem> filter = s -> userName.equals(s.getUserName());
		List<LockItem> curItem = lockItemArray.stream().filter(filter).collect(Collectors.toList());
		// if receive deny msg, then check if local has the usename with same secret, if
		// have then delete it
		if (isDeny) {
			if (!curItem.isEmpty()) {
				LockItem item = (LockItem) curItem.get(0);
				// if this server is the origin lock_request sending server
				// then should reply the client fail msg
				if (!item.getOriginCon().getIsServer()) {
					// Reply client the register fail msg
					registerFail(userName, item.getOriginCon());
				}

				// remove record item from array
				lockItemArray.remove(item);

				// Delete the local same username
				FileOperator.deleteUserName(userName);
			}
			// broadcast deny msg to other server
			broadcastMessage(con, msgObj.toJSONString(), true);
		} else {
			// if receive lock allow message, then check if every request has received
			// if all received, reply register success.
			if (!curItem.isEmpty()) {
				LockItem item = (LockItem) curItem.get(0);
				if (item.ifNeedReplyOrginCon()) {
					if (!item.getOriginCon().getIsServer()) {
						// Reply the register success message
						registerSuccess(userName, secret, item.getOriginCon());
					} else {
						// reply the origin server the lock allow msg
						item.getOriginCon().writeMsg(msgObj.toJSONString());
					}

					lockItemArray.remove(item);
				}
			}
		}
		return false;
	}
	
	/*
	 * save synchronized userinfo into local storage
	 */
	private synchronized boolean saveUserInfo(JSONObject msgObj) {
		String jsonString = (String) msgObj.get("userinfo");
		FileOperator.saveUserInfoToLocal(jsonString);
		
		// store logout user info into userManager
		jsonString = (String) msgObj.get("logoutUserInfos");
		Gson gson = new Gson();
		Type type = new TypeToken<ArrayList<LogoutUserInfo>>(){}.getType();
		ArrayList<LogoutUserInfo> arrayList = gson.fromJson(jsonString, type);
		userManager.setLogoutUserInfos(arrayList);
		
		// save ParentHostName remote hostname/port
		Settings.setParentHostNameOfRemote((String) msgObj.get("remotehostname"));
		Long portNum = (Long)msgObj.get("remoteport");
		Settings.setParentPortOfRemote(portNum.intValue());
		
		return false;
	}

	/*
	 * add by yicongLI 19-04-18 broadcast activities
	 */
	@SuppressWarnings("unchecked")
	private synchronized boolean activityMessage(Connection con, String message) {
		// check invalids
		JSONObject msgObject = null;
		JSONObject activity_message = null;
		try {
			msgObject = (JSONObject) parser.parse(message);
			if (!msgObject.containsKey("activity")) {
				responseInvalidMsg("Message does not contain an activity object", con);
				return true;
			}
			if (!msgObject.containsKey("username")) {
				responseInvalidMsg("Message does not contain receiver's username", con);
				return true;
			}
			if (!msgObject.containsKey("secret")) {
				responseInvalidMsg("Message does not contain receiver's secret", con);
				return true;
			}

			// parse activity
			activity_message = (JSONObject) msgObject.get("activity");
			if (activity_message == null) {
				activity_message = new JSONObject();
			}
		} catch (Exception e) {
			e.printStackTrace();
			responseInvalidMsg("Message parse error", con);
			return true;
		}

		// auth
		String userName = (String) msgObject.get("username");
		String secret = (String) msgObject.get("secret");
		
		int latestIndex = userManager.shouldAuthenticateUser(userName, secret, con);
		if (latestIndex != -1)  {
			activity_message.put("authenticated_user", userName);
			
			JSONObject msgObjFinal = new JSONObject();
			msgObjFinal.put("command", "ACTIVITY_BROADCAST");
			msgObjFinal.put("activity", activity_message);
			// add time stamp and index
			msgObjFinal.put("timestamp", new Long(new Date().getTime()));
			msgObjFinal.put("index", new Integer(latestIndex));
			
			// TODO check what we need in handling the index of user
			msgObjFinal.put("ip", con.getIPAddressWithPort());

			broadcastMessage(con, msgObjFinal.toJSONString(), false);
			
			return false;
		} else {
			responseInvalidMsg("User not authenticated.", con);
			return true;
		}
	}

	// added, modified and finished -- pateli
	private synchronized boolean broadcastActivities(Connection con, String message) {

		// check invalids
		JSONObject msgObject = null;
		try {
			msgObject = (JSONObject) parser.parse(message);
			if (!msgObject.containsKey("activity")
					|| msgObject.get("activity") == null) {
				responseInvalidMsg("Message does not contain an activity object", con);
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
			responseInvalidMsg("Message parse error", con);
			return true;
		}

		// broadcast here
		broadcastMessage(con, msgObject.toJSONString(), false);
		return false;
	}

	/*
	 * add by yicongLI 20-04-18 check authentication and store info after receiving
	 * announcement
	 */
	private synchronized boolean receiveAnnouncement(Connection con, JSONObject msgObj) {
		// check authentication of con
		if (!con.getIsServer()) {
			responseInvalidMsg("Message received from an unauthenticated server", con);
			return true;
		} else if (!msgObj.containsKey("id")) {
			responseInvalidMsg("Message does not contain the id field", con);
			return true;
		} else if (!msgObj.containsKey("hostname")) {
			responseInvalidMsg("Message does not contain the hostname field", con);
			return true;
		} else if (!msgObj.containsKey("port")) {
			responseInvalidMsg("Message does not contain the port field", con);
			return true;
		}

		// update local info
		Integer sameInfoIndex = -1;
		String msgID = (String) msgObj.get("id");
		for (int j = 0; j < announcementInfo.size(); j++) {
			String infoID = (String) announcementInfo.get(j).get("id");
			if (msgID.equals(infoID)) {
				sameInfoIndex = j;
				break;
			}
		}

		// if find the info exist in Arraylist, then replace the info
		// else add to local storage.
		con.setRemoteLitenerPort((Long)msgObj.get("port"));
		if (sameInfoIndex != -1) {
			announcementInfo.set(sameInfoIndex, msgObj);
		} else {
			announcementInfo.add(msgObj);
		}

		// broadcast message to other servers
		broadcastMessage(con, msgObj.toJSONString(), true);
		return false;
	}
	
	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con) {
		if (!term) {
			connections.remove(con);
			con.closeCon();
			
			// if lost connection with parent server, then reconnect server
			if (con.getIsRemoteServer()) {
				reconnectServer();
			}
		}
	}
	
	/*
	 * reconnect operation function
	 */
	public void reconnectServer () {
		resetReconnectOperation();

		int delay = 6000; // Milliseconds
        ActionListener taskTimer = new ActionListener() {
        	// updating method
            public void actionPerformed(ActionEvent evt) {
            	// if fail to change status, then don't reconnect the server
                if (changeReconnectStatus()) {
					initiateConnection();
				}
            }
        };

        // set timer for updating graphs
        reconnectTimer = new Timer(delay, taskTimer);
        reconnectTimer.start();
	}
	
	/*
	 * reset reconnect timer and time record
	 */
	public synchronized void resetReconnectOperation () {
		if (reconnectTimer != null) {
			reconnectTime = 0;
			reconnectTimer.stop();
			reconnectTimer = null;
		}
	}
	
	/*
	 * change reconnection status
	 * if return false, then stop reconnection
	 */
	public synchronized boolean changeReconnectStatus () {
		// if tick reach the end of whole simulate process
        reconnectTime ++;
        // if reconnect time larger than 3, then reconnect to parent server of remote host
        if (reconnectTime == 3) {
        	// if remote server has parent server, then connect to this parent server
        	if (Settings.getParentHostNameOfRemote() != null) {
        		Settings.setRemoteHostname(Settings.getParentHostNameOfRemote());
    			Settings.setRemotePort(Settings.getParentPortOfRemote());
			}
        	else {
        		// if remote server doesn't have parent server(it is root server), then connect to first sibling
        		JSONObject serverInfo = findSiblingServer();
        		if (serverInfo != null) {
        			Settings.setRemoteHostname((String)serverInfo.get("hostname"));
        			// the Long value has been convert into Double by Gson
        			Settings.setRemotePort(((Double)serverInfo.get("port")).intValue());
				}
        		else {
        			// if this server is the first sibling, then treat itself as the new root server
            		Settings.setRemoteHostname(null);
        			Settings.setRemotePort(0);
					resetReconnectOperation();
					return false;
				}
        	}
        }
        // if reconnect more than 4 times, then exit program directly
        else if (reconnectTime == 5) {
			System.exit(-1);
		}
        
        return true;
	}

	/*
	 * find the first available sibling server
	 */
	private JSONObject findSiblingServer () {
		for (JSONObject jsonObject : announcementInfo) {
			String hostname 	= (String)jsonObject.get("hostname");
			Long port 			= (Long)  jsonObject.get("port");
			
			String remoteHostname = Settings.getRemoteHostname();
			int remotePort = Settings.getRemotePort();
			
			if (remoteHostname.equals("localhost") || remoteHostname.equals("127.0.0.1")) {
				remoteHostname = Settings.getIp();
			}
			
			// find the remote server from server announcements
			if (hostname.equals(remoteHostname) && port == remotePort) {
				String jsonString 	= (String)jsonObject.get("children");
				Gson childrenServerInfo = new Gson();
				Type type = new TypeToken<ArrayList<JSONObject>>(){}.getType();
				// get all children server of the remote server
				// the Long value will be convert into Double
				ArrayList<JSONObject> arrayList = childrenServerInfo.fromJson(jsonString, type);
				
				// all the server connect to the first sibling, the first one become the root server
				// if remote server only have one child server, then this server is the new root server
				if (arrayList.size() > 1) {
					// get first sibling server
					JSONObject serverInfo = arrayList.get(0);
					String childHostname = (String)serverInfo.get("hostname");
					int childport = ((Double)serverInfo.get("port")).intValue();
					
					// if current server is not the first server, then connect to this server
					// otherwise treat current server as root server
					if (!(childHostname.equals(Settings.getIp()) && childport == Settings.getLocalPort())) {
						return serverInfo;
					}
				}
			}
		}
		
		return null;
	}
	
	/*
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException {
		Connection c = new Connection(s);
		log.debug("incomming connection: " + c.getIPAddressWithPort());
		connections.add(c);
		return c;

	}

	/*
	 * A new outgoing connection has been established, and a reference is returned to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException {
		Connection c = new Connection(s);
		log.debug("outgoing connection: " + c.getIPAddressWithPort());
		connections.add(c);
		return c;
	}

	@Override
	public void run() {
		log.info("using activity interval of " + Settings.getActivityInterval() + " milliseconds");
		while (!term) {
			// do something with 5 second intervals in between
			try {
				regularAnnouncement(); // add by yicongLI 23-04-18 start regular announcement
				checkRedirection(); // check redirection and redirect one client.
				
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("received an interrupt, system is shutting down");
				break;
			}
		}
		log.info("closing " + connections.size() + " connections");
		// clean up
		for (Connection connection : connections) {
			connection.closeCon();
		}
		
		listener.setTerm(true);
	}
	
	/*
	 * add by yicongLI 23-04-18 broadcast server load state to the other servers
	 */
	@SuppressWarnings("unchecked")
	private synchronized void regularAnnouncement() {
		JSONObject msgObj = new JSONObject();
		msgObj.put("command", "SERVER_ANNOUNCE");
		msgObj.put("id", uniqueID);
		msgObj.put("load", clientLoadNum());
		msgObj.put("hostname", Settings.getIp());
		msgObj.put("port", Settings.getLocalPort());
		
		// put children server address info into announcement
		Gson childrenServerInfo = new Gson();
		String childrenInfo = childrenServerInfo.toJson(childrenServerInfo());
		msgObj.put("children", childrenInfo);
		
		broadcastMessage(null, msgObj.toJSONString(), true);
	}
	
	/*
	 * return current client load count 
	 */
	private synchronized Integer clientLoadNum () {
		Integer load = 0;

		for (Connection con : connections) {
			if (!con.getIsServer()) {
				load++;
			}
		}
		
		return load;
	}
	
	/*
	 * get all children server IP address info
	 */
	@SuppressWarnings("unchecked")
	private synchronized ArrayList<JSONObject> childrenServerInfo () {
		ArrayList<JSONObject> childrenInfo = new ArrayList<JSONObject>();
		for (Connection con : connections) {
			// filter con of children server
			if (con.getIsServer() && !con.getIsRemoteServer() && con.getRemoteLitenerPort() != 0) {
				JSONObject info = new JSONObject();
				info.put("hostname", con.getIPAddress());
				info.put("port", con.getRemoteLitenerPort());
				
				childrenInfo.add(info);
			}
		}
		
		return childrenInfo;
	}
	
    /*
     * check redirect once 5 seconds, redirect one client a time
     */
	private void checkRedirection () {
		Connection redirectCon = null;
		for (Connection curCon : connections) {
			if (!curCon.getIsServer()) {
				if (redirectionLogin(curCon)) {
					redirectCon = curCon;
				}
			}
		}
		
		if (redirectCon != null) {
			connectionClosed(redirectCon);
		}
	}

	public final void setTerm(boolean t) {
		term = t;
	}

	public final ArrayList<Connection> getConnections() {
		return connections;
	}
}