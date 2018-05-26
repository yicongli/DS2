The default local port number for both client and server is 3780

1. Start client:
java -jar ActivityStreamerClient.jar -rp <serverPortNumber> -rh <anyServerName> -lp <localPortNumber> -u <userName> -s <secret>

  Example:
    java -jar ActivityStreamerClient.jar -rp 3780 -rh localhost -lp 3500 -u "test" -s "asdfasdn"
  Explaination:
    - if command line has neither -u nor -s params, login as anonymous
    - if command line only has -u param, register the user with username and the client will generate the secret for user
    - if command line has both -u and -s params, login with username and secret

2. Start server:
java -jar ActivityStreamerServer.jar -rp <anyPortNumber> -rh <anyServerName> -lp <localPortNumber>

  Example:
- Run first: java -jar ActivityStreamerServer.jar
- Run next: java -jar ActivityStreamerServer.jar -rp 3780 -rh localhost -lp 3781
    Explaination:
    - if command line has no params, server starts as the first server and does not connect to the others
    - if command line has both -rp and -rh params, server starts as the server connecting to the remote server
    - if command line has all params, server starts as the server connecting to the remote server with specific local port number
