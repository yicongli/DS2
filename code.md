1. Client (thao)
	- Redirect when server crashes
	- Send the messages again if connection crashes?? 
		+ Use buffer to save?
	
2. Server
	- Server joins network at any time: (rick)
		+ synchronize all information from two servers connected to each other
	- Fix register: cannot register at the same time (thao)
		+ put timestamps, reject smaller timestamp
	- Fix broadcasting func: (rick)
		+ A client will send the message to all other clients at time X (even it log out before receiving the messages)
		+ Messages are sent in the same order at the receiving client
	- Create a function to do: 
		+ Clients are evently distributed over the servers
	- If server crashes:
		+ Reconnect the clients to other servers
		+ Evenly distributed the clients
	- If client crashes: 
		+ Evenly distributed the clients

3. Report:
	- Eventual Consistency
		
	
