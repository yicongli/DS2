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
	public static void createNewFile () {
		saveUserName("anonymous", "");
	} 
	
	/*
	 * create by yicongLI 14-05-2018
	 * save user name and password
	 */
	@SuppressWarnings("unchecked")
	public static synchronized boolean saveUserName(String name, String password) {
		try {
			FileWriter filewriter = new FileWriter(FileOperator.fileName);
			JSONObject obj = new JSONObject();
			obj.put(name, password);
			filewriter.write(obj.toJSONString());
			filewriter.flush();
			filewriter.close();
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/*
	 * create by yicongLI 14-05-2018
	 * delete user name
	 */
	public static synchronized boolean deleteUserName(String name) {
		try {
			Reader in = new FileReader(FileOperator.fileName);
			JSONObject userlist = (JSONObject) Control.parser.parse(in);
			if (userlist.containsKey(name)) {
				userlist.remove(name);
				FileWriter file = new FileWriter(FileOperator.fileName);
				file.write(userlist.toJSONString());
				file.flush();
				file.close();
				
				return true;
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	// Added by thaol4
	// check if local storage contains username
	public static synchronized String checkLocalStorage(String username) {
		if (!FileOperator.checkFileExist()) {
			FileOperator.createNewFile();
			return username.equalsIgnoreCase("anonymous") ? "" : null;
		}
		
		try {
			Reader in = new FileReader(FileOperator.fileName);
			JSONObject userlist = (JSONObject) Control.parser.parse(in);
			// username is found
			if (userlist.containsKey(username)) {
				return (String)userlist.get(username);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		// username is not found
		return null;
	}
	
	/*
	 * check file exist 
	 */
	public static synchronized boolean checkFileExist() {
		File f = new File(FileOperator.fileName);
		return f.exists();
	}
}
