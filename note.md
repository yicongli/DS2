1. Overall:
	- allow servers to join the network at any time (after clients have joined)
	- register once: at previous project, can register at the same time with the same user name
	- guanrantees that an activity message sent by a client reaches all clients that are connected to the network at the time that the message was sent (timestamp)
		+ if the client log out before receiving the message, save that message and remember the client. Send the msg again when the client log in

	- guarantees that all activity messages sent by a client are delivered in the same order at each receiving client
	- clients are evenly distributed over the servers

2. Failure:
	- Servers crash:
		- reconnect the clients
		- redirect to balance the requests and evenly distributed

	- Clients crash:
		- evenly distributed

3. Availability: Clients should always be able send messages if they are logged in and receive messages as soon as possible

4. Consistency: 
	- msg sent should eventually get to the clients as required
	- clients should eventually be load balanced over the servers
	- the delivery order of messages should be preserved

5. Topology: high-availability seamless redundancy
