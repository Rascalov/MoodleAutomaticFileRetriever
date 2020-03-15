package com.mafrau.Mafr;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlEmailInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;

public class Automater {
	private final String LOGIN_FORM_URL = "https://moodle.inholland.nl/auth/saml2/login.php?wants&idp=75681424e34ca7710fa9a3bf0b398bd2&passive=off";
	private final String SUCCES_PAGE = "https://moodle.inholland.nl/my/";
	private WebClient webClient;
	private String moodleCourseUrl, downloadPath;
	private HtmlPage page;
	private MoodleDownloader downloader;
	private String username, password; // Necessary later on, when the site attempts to block access, we hop to a new client and relog 
	
	public Automater(WebClient webClient, String moodleCourseUrl, String downloadPath) {
		this.webClient = webClient;
		this.moodleCourseUrl = moodleCourseUrl;
		this.downloadPath = downloadPath;
		this.downloader = new MoodleDownloader(webClient);
	}
	public boolean login(String username, String password) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		page = webClient.getPage(LOGIN_FORM_URL);
		
		HtmlForm form = page.getForms().get(0);
        HtmlEmailInput uname =  form.getInputByName("UserName");
        HtmlPasswordInput pass =  form.getInputByName("Password"); 
        HtmlElement buttonElement = form.getElementsByTagName("span").get(1);
        uname.setValueAttribute(username);
        pass.setValueAttribute(password);    
        page = buttonElement.click();
        
        // Surfspot attempts to redirect you to a page where robots can't click on to continue, I made a temp solution
        // Clean solution will be made whenever I feel like figuring out all data the form sends and sending a replica request
        DomElement BypassButton = page.createElement("button");
        BypassButton.setAttribute("type", "submit");
        HtmlForm formBypass = page.getForms().get(0);
        formBypass.appendChild(BypassButton);
        page = BypassButton.click();
        
		// for the check you can simply use the current url of the client, if it is on moodle: true. else: false
		if(page.getUrl().toString().equals(SUCCES_PAGE)) {
			this.username = username;
			this.password = password;
			return true;
		}
		return false;
	}
	
	public void downloadCourseItems() throws FailingHttpStatusCodeException, MalformedURLException, IOException, InterruptedException {
		
		page = webClient.getPage(moodleCourseUrl);
		String CourseName = page.querySelector("div.page-header-headings").asText();
		CourseName = removeIllegalCharsFromFileName(CourseName);
		this.downloadPath += "/" + CourseName; // Maybe move this to the constructor, or find a better way of doing this
		new File(this.downloadPath).mkdir();
		System.out.println("Created Directory for: " + this.downloadPath);
		DomElement menu = page.querySelector("div.mt-sitemenus");
		System.out.println("Getting Map structure...");
		var structure = determineMapStructure(menu); 
		System.out.println("Map Structure received. Fetching clean url...");
		// now, foreach folder structure, we call 
		String cleanurl = getCleanUrl();
		System.out.println("Clean url fetched: " + cleanurl);
		
		System.out.println("Downloading Course Subjects...");
		for (ArrayList<MoodleFolder> courseSubject : structure) {
			System.out.println("------------------ Head folder------------------------");
			String headFolder = this.downloadPath + "/" + courseSubject.get(0).name;
			new File(headFolder).mkdir(); // Head Dir
			System.out.println("Created: " + headFolder);
			// the first folder is the Head dir, so he needs a seperate foreach and then be removed from courseSubject list
			System.out.println("Getting content for: " + courseSubject.get(0).name);
			DomNode content  = getContent(cleanurl + "#section-" + courseSubject.get(0).sectionId);
			DomNodeList<DomNode> downloadLinks = content.querySelectorAll("a");
			for (DomNode initLink : downloadLinks) {
				if(!((DomElement)initLink.getParentNode()).getTagName().equals("h3")) {
					System.out.println("Name: " + initLink.asText());
					System.out.println("Resource Link: " + ((DomElement)initLink).getAttribute("href") );
					downloader.downloadFile(((DomElement)initLink).getAttribute("href"), this.downloadPath + "/"+ courseSubject.get(0).name);
					System.out.println();
				}
			}
			// now remove the first folder from the list
			String courseSubjectName = courseSubject.get(0).name;
			courseSubject.remove(0);
			System.out.println("------------------Sub Folder(s)------------------");
			for (MoodleFolder folder : courseSubject) {
				new File(this.downloadPath + "/" + courseSubjectName + "/" + folder.name).mkdir(); // Sub Dir
				System.out.println("Folder " + folder.name + " has the following links:");
				content  = getContent(cleanurl + "#section-" + folder.sectionId);
				downloadLinks = content.querySelectorAll("a");
				for (var link : downloadLinks) {
					if(!((DomElement)link.getParentNode()).getTagName().equals("h3")) { // check prevents headline link to be added
						System.out.println("Name: " + link.asText());
						System.out.println("Resource Link: " + ((DomElement)link).getAttribute("href") );
						downloader.downloadFile(((DomElement)link).getAttribute("href"), this.downloadPath + "/"+ courseSubjectName + "/"+ folder.name);
						System.out.println();
					}
				}
				System.out.println();
			}
		}
		System.out.println("Finished Downloading");
	}

	private DomNodeList<DomNode> getMoodleMaterial(DomNode content){
		// Goal is to get resource links per li of the ul
		// TODO see if this is necessary

		return null;
	}
	private ArrayList<MoodleFile> getCurrentFiles(File folder){
		// goals is to return all files of the current path and it's following folders
		for (File file : folder.listFiles()){
			if(file.isDirectory()){

			}
		}
		return null;
	}

	
	private ArrayList<ArrayList<MoodleFolder>> determineMapStructure(DomNode menu) {
		// Determine the map structure based on the given menu. We also are able to get the data-sections the menu has. 
		// The courses I collected did not go further than a 4 layer course system, maybe it is the limit
		// TODO Rework the method of finding submenus, is is awful atm 
	    var folderStructureArrayList = new ArrayList<ArrayList<MoodleFolder>>();
		for (var element : menu.getChildren()) { // foreach Course Subject basically
			ArrayList<MoodleFolder> courseSubject =  new ArrayList<MoodleFolder>();
			String sectionID = ((DomElement)element.querySelector("a.mt-heading-btn")).getAttribute("data-section");
			MoodleFolder headFolder = new MoodleFolder(element.asText(), Integer.parseInt(sectionID) );	
			courseSubject.add(headFolder); // first object of the array is always the main folder
			// need to check for sub menu and gather its folders
		    DomNode subMenu = element.querySelector("div.list-group");
			if(subMenu != null) {
				for (DomNode node : subMenu.getChildNodes()) {
					if(((DomElement)node).getTagName().equals("div")) {
						// if it is a submenu again, another foreach
						for (DomNode subNode  : node.getChildNodes()) {
							// and another foreach if it's submenu level 3.... There must be a better way to handle this
							if(((DomElement)subNode).getTagName().equals("div")) {
								for (DomNode subsubNode : subNode.getChildNodes()) {
									
									// it allows layer 4... Need a better method for this asap
 									if(((DomElement)subsubNode).getTagName().equals("div")) {
										for (DomNode subsubsubNode : subsubNode.getChildNodes()) {
											int dataSectionId = Integer.parseInt(((DomElement)subsubsubNode).getAttribute("data-section"));
											courseSubject.add(new MoodleFolder(subsubsubNode.getTextContent(), dataSectionId));
										}
									}
									else {
										int dataSectionId = Integer.parseInt(((DomElement)subsubNode).getAttribute("data-section"));
										courseSubject.add(new MoodleFolder(subsubNode.getTextContent(), dataSectionId));
									}
								}
							}
							else {
								int dataSectionId = Integer.parseInt(((DomElement)subNode).getAttribute("data-section"));
								courseSubject.add(new MoodleFolder(subNode.getTextContent(), dataSectionId));
							}
						}
					}
					else {
						int dataSectionId = Integer.parseInt(((DomElement)node).getAttribute("data-section"));
						courseSubject.add(new MoodleFolder(node.getTextContent(), dataSectionId));
					}
				}
			}
			folderStructureArrayList.add(courseSubject);
		}
		return folderStructureArrayList;
	}
	private String getCleanUrl() {
		if(this.moodleCourseUrl.contains("&")) {
			// Need to remove  sectionid and section number in order to get the base url to navigate through the different course subjects
			return this.moodleCourseUrl.substring(0, this.moodleCourseUrl.indexOf('&'));
		}
		else {
			return this.moodleCourseUrl;
		}
	}
	private DomNode getContent(String url) throws InterruptedException, FailingHttpStatusCodeException, MalformedURLException, IOException {
		// This returns the current content displayed on the right side
		this.page = this.webClient.getPage(url);
		DomNode contentSection = page.querySelector("ul#mt-sectioncontainer");
		int attempts = 1;
		while (contentSection.asText().equals("")) {
			// it can take some time for content to be displayed, so wait untill it does
			System.out.println("Failed to get content. Trying again.. attempt: " +  attempts); 
			Thread.sleep(200);
			attempts++;
			if(attempts == 25) {
				// We are either getting blocked, or something accidental is blocking access to the content
				// refreshing the page does nothing, but restarting the client and relogging proved to work in all current cases
				System.out.println(attempts + " attempts made, refreshing...");
				this.Reset();
				System.out.println("Relogging...");
				System.out.println(login(this.username, this.password) ? "Relogged": "failed to relog");
				this.page = webClient.getPage(url);
				System.out.println("\n Getting new content...");
				page = this.webClient.getPage(url);
				Thread.sleep(2000);
				contentSection = page.querySelector("ul#mt-sectioncontainer");
				attempts = 1;
			}
		}
		return contentSection;
	}
	private void Reset(){
		this.webClient.close();
		this.webClient = new WebClient(BrowserVersion.FIREFOX_60);
		webClient.getOptions().setJavaScriptEnabled(true);
		webClient.getCookieManager().setCookiesEnabled(true);
		webClient.getOptions().setRedirectEnabled(true);
	}
	private String removeIllegalCharsFromFileName(String filename) {
		return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
	}

}
