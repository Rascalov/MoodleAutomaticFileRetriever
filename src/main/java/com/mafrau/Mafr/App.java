package com.mafrau.Mafr;

import java.io.Console;
import java.io.IOException;
import java.net.MalformedURLException;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import org.apache.commons.cli.*;

/**
 * Hello world!
 *
 */
public class App
{
	public static void main( String[] args ) throws FailingHttpStatusCodeException, MalformedURLException, IOException, InterruptedException, ParseException {
		// logging is such a mess, so make it piss off
		java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF);
		java.util.logging.Logger.getLogger("org.apache.http").setLevel(java.util.logging.Level.OFF);

		WebClient webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
		webClient.getOptions().setJavaScriptEnabled(true);
		webClient.getCookieManager().setCookiesEnabled(true);
		webClient.getOptions().setRedirectEnabled(true);



		String url, path, username;
		char[] passwordArray;

		CommandLineParser parser = new DefaultParser();
		Options options = new Options();

		Option loginOption = Option.builder().argName("l").numberOfArgs(2).valueSeparator(' ').longOpt("login").build();
		options.addOption("u", true, "update a directory by a moodle course");
		options.addOption("d", true, "Specifies the directory");
		options.addOption(loginOption);

		CommandLine cmd = parser.parse(options, args);

		if(cmd.hasOption("u") && cmd.hasOption("login") && cmd.hasOption("d")){ // for the shell script
			url = cmd.getOptionValue("u");
			path = cmd.getOptionValue("d");
			username = cmd.getOptionValues("login")[0];
			passwordArray = cmd.getOptionValues("login")[1].toCharArray();
		}
		else if(cmd.hasOption("u")){
			System.out.println("Mafr Updating Mode");
			url = cmd.getOptionValue("u");
			Console console = System.console();
			if (console == null) {
				System.out.println("Couldn't get Console instance");
				System.exit(0);
			}
			username = console.readLine("Enter your inholland mail: ");
			passwordArray = console.readPassword("Enter your password: ");
			path = console.readLine("Enter directory path: ");
		}
		else{
			Console console = System.console();
			if (console == null) {
				System.out.println("Couldn't get Console instance");
				System.exit(0);
			}
			username = console.readLine("Enter your inholland mail: ");
			passwordArray = console.readPassword("Enter your password: ");
			url = console.readLine("Enter ENROLLED moodle course url: ");
			path = console.readLine("Enter directory path: ");
		}

		Automater attempt = new Automater(webClient, url, path);
		if(attempt.login(username, new String(passwordArray))) {
			System.out.println("Logged in");
			if(cmd.hasOption("u")){
				while(true){
					attempt.updateFiles();
					webClient.close();
					webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
					webClient.getOptions().setJavaScriptEnabled(true);
					webClient.getCookieManager().setCookiesEnabled(true);
					webClient.getOptions().setRedirectEnabled(true);
					attempt = new Automater(webClient, url, path);
					if(attempt.login(username, new String(passwordArray))){
						System.out.println("Relogged");
					}
					else{
						System.out.println("Unable to login");
						System.exit(0);
					}
				}
			}
			else {
				System.out.println("__________________________HERE WE GO__________________________");
				attempt.downloadCourseItems();

			}
		}
		else {
			System.out.println("Failed to log in, check your credentials");
			System.exit(0);
		}

	}
}
