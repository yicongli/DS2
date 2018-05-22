
1. Client (thao)
	- Redirect when server crashes
	- Send the messages again if connection crashes?? 
		+ Use buffer to save?
	
2. Server
	- Server joins network at any time: (rick)
		+ synchronize all information from two servers connected to each other
	- Fix register: cannot register at the same time (thao)
		+ put timestamps, reject smaller timestamp
        + ! should be able to register at anytime
	- Fix broadcasting func: (rick)
		+ A client will send the message to all other clients at time X (even it log out before receiving the messages)
		+ Messages are sent in the same order at the receiving client
	- Create a function to do: 
		+ Clients are evently distributed over the servers
	- If server crashes:
		+ Evenly distributed the clients

3. Report:
	- Eventual Consistency
    - "If some figures span two columns then put them at the top of bottom of the page, spanning two columns."
   

4. Aaron said there is no new client in the Asg2, this means, when a server crashed without any notification or the connection between server and client interruptes, client won't be redirect to the other servers and just lost the connection.
    - "If you think some goals can't be reached then document that in your report."
5. if root server crashed, the rest server should join together and choose one as new root server, choose the one with most load as root server
		
6. Activity is not broadcasted to all the servers and hence, all the clients. the broadcast message has been altered by your implementation as displayed on my server. for example, {"1":"1"} is displayed as {"1":"1"}. Moreover, the activity message sent is not shown properly at other client side, for eg. C2 client sends {"f":"F"}, the message is broadcasted as C2:F.

7. Potential extension: maintain a server-tree in each server, when reconnect, search the server in this tree