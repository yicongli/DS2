/* Purpose: do the operations in the local storage (json file)
 * 
 * 1. Get all user info in the file
 * 2. Save user info to the file
 * 3. Delete user info from the file
 * 4. Check if user is existed in file
 */

package activitystreamer.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class FileOperator {
	public static String fileName = String.valueOf(Settings.getLocalPort()) + ".json";

	/*
	 * create by yicongLI 14-05-2018 create new User file
	 */
	public static void createNewFile(File f) {
		try {
			f.createNewFile();
			saveUserName("anonymous", "", new Long(0));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * get all user info
	 */
	public static synchronized JSONObject allUserInfo() {
		File f = new File(FileOperator.fileName);
		if (!f.exists()) {
			createNewFile(f);
		}

		try {
			Reader in = new FileReader(FileOperator.fileName);
			JSONObject userlist = (JSONObject) Control.parser.parse(in);
			return userlist;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return new JSONObject();
	}

	/*
	 * create by yicongLI 14-05-2018 save user name and password
	 */
	@SuppressWarnings("unchecked")
	public static synchronized void saveUserName(String name, String password, Long registerTime) {
		JSONObject obj = allUserInfo();
		
		JSONObject infoObj = new JSONObject();
		infoObj.put("password", password);
		infoObj.put("registertime", registerTime);
		obj.put(name, infoObj);
		saveUserInfoToLocal(obj.toJSONString());
	}

	/*
	 * create by yicongLI 14-05-2018 delete user name
	 */
	public static synchronized boolean deleteUserName(String name) {
		JSONObject userlist = allUserInfo();
		if (userlist.containsKey(name)) {
			userlist.remove(name);
			saveUserInfoToLocal(userlist.toJSONString());

			return true;
		}

		return false;
	}

	// Added by thaol4
	// check if local storage contains username
	public static synchronized String checkLocalStorage(String username) {
		File f = new File(FileOperator.fileName);
		if (!f.exists()) {
			createNewFile(f);
			return username.equalsIgnoreCase("anonymous") ? "" : null;
		}
		JSONObject userlist = allUserInfo();
		// username is found
		if (userlist.containsKey(username)) {
			return (String) userlist.get(username);
		}
		// username is not found
		return null;
	}

	/*
	 * write user info to local file
	 */
	public static synchronized void saveUserInfoToLocal(String jsonString) {
		try {
			FileWriter file = new FileWriter(FileOperator.fileName);
			file.write(jsonString);
			file.flush();
			file.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * synchronize the user info between local and remote server
	 * @param incomeUserInfoStr the user info json str from remote server
	 * @param incomeCon			the connection with remote server
	 */
	@SuppressWarnings("unchecked")
	public static synchronized void synchroniseNewUserInfo (String incomeUserInfoStr, Connection incomeCon) {
		JSONObject incomeList = null;
		JSONObject localList = FileOperator.allUserInfo();
		
		try {
			incomeList = (JSONObject) Control.parser.parse(incomeUserInfoStr);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		// filter the new user information from the other server
		JSONObject newUserForCon = new JSONObject();
		JSONObject newUserForLocal = new JSONObject();
		
		for (Object key : localList.keySet()) {
			if (incomeList.containsKey(key)) {
				JSONObject localInfo = (JSONObject) localList.get(key);
				JSONObject incomeInfo = (JSONObject) incomeList.get(key);
				// if two userInfo data has same username with different password
				// then check which one has latest register time
				if (!localInfo.get("password").equals(incomeInfo.get("password"))) {
					// if local info register later, then override the local user information
					if ((Long)localInfo.get("registertime") > (Long)incomeInfo.get("registertime")) {
						newUserForLocal.put(key, incomeInfo.get(key));
					// otherwise override the remote userinfo
					} else {
						newUserForCon.put(key, localInfo.get(key));
					}
				}
			// if there are unknown userinfo in local, then add them to remote userinfo
			} else {
				newUserForCon.put(key, localList.get(key));
			}
		}
		
		// if there are unknown userinfo in local, then add them to remote userinfo
		for (Object key : incomeList.keySet()) { 
			if (!localList.containsKey(key)) {
				newUserForLocal.put(key, incomeList.get(key));
			}
		}
		
		// send new user to this income connection, which will check local storage and broadcast to the others
		if (newUserForCon.size() > 0) {
			JSONObject newJsonUserInfoForCon = new JSONObject();
			newJsonUserInfoForCon.put("command", "NEW_REGISTERED_USER");
			newJsonUserInfoForCon.put("userinfo", newUserForCon);
			incomeCon.writeMsg(newJsonUserInfoForCon.toJSONString());
		}
		
		if (newUserForLocal.size() > 0) {
			// save new user to local and broadcast to all the other connected servers except this income one
			JSONObject newJsonUserInfoForLocal = new JSONObject();
			newJsonUserInfoForLocal.put("command", "NEW_REGISTERED_USER");
			newJsonUserInfoForLocal.put("userinfo", newUserForLocal);
			Control.getInstance().broadcastMessage(incomeCon, newJsonUserInfoForLocal.toJSONString(), true);
			// logout any user with wrong secret
			Control.clientInfoManager.checkLoggingoutClients(newUserForLocal);
			// when receive new user, check local login user, kick off the user with wrong password
			FileOperator.saveNewUserInfo(newUserForLocal);
		}
	}
	
	/*
	 * Save new user information to local storage
	 */
	@SuppressWarnings("unchecked")
	public static synchronized void saveNewUserInfo(JSONObject newUserForLocal) {
		JSONObject localUserInfo = allUserInfo();
		for (Object key : newUserForLocal.keySet()) {
			localUserInfo.put(key, newUserForLocal.get(key));
		}
		
		saveUserInfoToLocal(localUserInfo.toJSONString());
	}
}
