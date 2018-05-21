package activitystreamer.server;
import java.util.ArrayList;
import activitystreamer.util.Settings;

public class UserManager {
	
	protected static UserManager manager = null;
	
	private ArrayList<LogoutUserInfo> logoutUserInfos = null;
	private ArrayList<LoginUserInfo> loginUserInfos = null;
	
	public static UserManager getInstance() {
		if (manager == null) {
			manager = new UserManager();
		}
		
		return manager;
	}
	
	public UserManager() {
		logoutUserInfos = new ArrayList<LogoutUserInfo>();
		loginUserInfos  = new ArrayList<LoginUserInfo>();
	}
	
	public void saveLogoutTime(Connection con) {
		String name = null;
		String ip = null;
		if (con.username().equalsIgnoreCase("anonymous")) {
			name = "";
			ip = Settings.socketAddress(con.getSocket());
		}
		else {
			name = con.username();
			ip = "";
		}

		logoutUserInfos.add(new LogoutUserInfo(name, ip));
	}
}
