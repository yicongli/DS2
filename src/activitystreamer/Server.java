package activitystreamer;


import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import activitystreamer.server.Control;
import activitystreamer.util.Settings;

public class Server {
	private static final Logger log = LogManager.getLogger();
	
	// UI BASIS
	private static void help(Options options){
		String header = "An ActivityStream Server for Unimelb COMP90015\n\n";
		String footer = "\ncontact aharwood@unimelb.edu.au for issues.";
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("ActivityStreamer.Server", header, options, footer, true);
		System.exit(-1);
	}
	
	public static void main(String[] args) {
		
		log.info("reading command line options");
		// IUI OPTIONS	
		Options options = new Options();
		options.addOption("lp",true,"local port number");
		options.addOption("rp",true,"remote port number");
		options.addOption("rh",true,"remote hostname");
		options.addOption("lh",true,"local hostname");
		options.addOption("a",true,"activity interval in milliseconds");
		options.addOption("s",true,"secret for the server to use");
		
		
		// build the parser
		CommandLineParser parser = new DefaultParser();
		
		// parse passed parameters for server
		CommandLine cmd = null;
		try {
			cmd = parser.parse( options, args);
		} catch (ParseException e1) {
			help(options);
		}
		
		// check local port number
		if(cmd.hasOption("lp")){
			try{
				int port = Integer.parseInt(cmd.getOptionValue("lp"));
				// and set it
				Settings.setLocalPort(port);
			} catch (NumberFormatException e){
				// or throw "requisite" exception
				log.info("-lp requires a port number, parsed: "+cmd.getOptionValue("lp"));
				help(options);
			}
		}
		
		// set remote host name
		if(cmd.hasOption("rh")){
			Settings.setRemoteHostname(cmd.getOptionValue("rh"));
		}
		
		// check remote port number
		if(cmd.hasOption("rp")){
			try{
				int port = Integer.parseInt(cmd.getOptionValue("rp"));
				// and set it
				Settings.setRemotePort(port);
			} catch (NumberFormatException e){
				// or exception
				log.error("-rp requires a port number, parsed: "+cmd.getOptionValue("rp"));
				help(options);
			}
		}
		
		// check activity interval for server announce
		if(cmd.hasOption("a")){
			try{
				int a = Integer.parseInt(cmd.getOptionValue("a"));
				// and set it
				Settings.setActivityInterval(a);
			} catch (NumberFormatException e){
				// or exception
				log.error("-a requires a number in milliseconds, parsed: "+cmd.getOptionValue("a"));
				help(options);
			}
		}
		
		// automatic handling
		// get local host name and set it for the server
		try {
			Settings.setLocalHostname(InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			log.warn("failed to get localhost IP address");
		}
		
		// set local host name manually
		// (if present)
		if(cmd.hasOption("lh")){
			Settings.setLocalHostname(cmd.getOptionValue("lh"));
		}

		if(cmd.hasOption("s")){
			Settings.setServerSecret(cmd.getOptionValue("s"));
		}
		
		log.info("starting server");
		
		
		final Control c = Control.getInstance(); 
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {  
				c.setTerm(true);
				c.interrupt();
		    }
		 });
	}

}
