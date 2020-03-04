package com.mafrau.Mafr;

import java.io.Console;
import java.io.IOException;
import java.net.MalformedURLException;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args ) throws FailingHttpStatusCodeException, MalformedURLException, IOException, InterruptedException
    {
    	// logging is such a mess, so make it piss off
	    java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF);
	    java.util.logging.Logger.getLogger("org.apache.http").setLevel(java.util.logging.Level.OFF);
	    
	    final WebClient webClient = new WebClient(BrowserVersion.FIREFOX_60);
		webClient.getOptions().setJavaScriptEnabled(true); 
		webClient.getCookieManager().setCookiesEnabled(true);
		webClient.getOptions().setRedirectEnabled(true);
	    Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get Console instance");
            System.exit(0);
        }
        String username = console.readLine("Enter your inholland mail: ");
        char[] passwordArray = console.readPassword("Enter your password: ");
        
        String url = console.readLine("Enter ENROLLED moodle course url: ");
        String path = console.readLine("Enter directory path: ");
        
        Automater attempt = new Automater(webClient, url, path);
		if(attempt.login(username, new String(passwordArray))) {
			System.out.println("Logged in");
			System.out.println("__________________________HERE WE GO__________________________");
			attempt.downloadCourseItems();
		}
		else {
			System.out.println("Failed to log in, check your credentials");
			System.exit(0);
		}
        
    }
}
