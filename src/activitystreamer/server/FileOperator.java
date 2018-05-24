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
	 * create by yicongLI 14-05-2018
	 * create new User file
	 */
	public static void createNewFile (File f) {
		try {
			f.createNewFile();
			saveUserName("anonymous", "");
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
	 * create by yicongLI 14-05-2018
	 * save user name and password
	 */
	@SuppressWarnings("unchecked")
	public static synchronized void saveUserName(String name, String password) {
		JSONObject obj = allUserInfo();
		obj.put(name, password);
		saveUserInfoToLocal(obj.toJSONString());
	}
	
	/*
	 * create by yicongLI 14-05-2018
	 * delete user name
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
			return (String)userlist.get(username);
		}
		
		// username is not found
		return null;
	}
	
	/*
	 * write user info to local file 
	 */
	public static synchronized void saveUserInfoToLocal (String jsonString) {
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
}
