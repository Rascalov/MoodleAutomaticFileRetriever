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
	private String username, password; // Necessary later on, when the site attempts to block access, we hop to a new client and relog 
	
	public Automater(WebClient webClient, String moodleCourseUrl, String downloadPath) {
		this.webClient = webClient;
		this.moodleCourseUrl = moodleCourseUrl;
		this.downloadPath = downloadPath;
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
					downloadFile(((DomElement)initLink).getAttribute("href"), this.downloadPath + "/"+ courseSubject.get(0).name);
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
					if(!((DomElement)link.getParentNode()).getTagName().equals("h3")) {
						System.out.println("Name: " + link.asText());
						System.out.println("Resource Link: " + ((DomElement)link).getAttribute("href") );
						downloadFile(((DomElement)link).getAttribute("href"), this.downloadPath + "/"+ courseSubjectName + "/"+ folder.name);
						System.out.println();
					}
				}
				System.out.println();
			}
		}
		System.out.println("Finished Downloading");
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
			if(attempts == 30) {
				// We are either getting blocked, or something accidental is blocking access to the content
				// refreshing the page does nothing, but restarting the client and relogging proved to work in all current cases
				System.out.println("30 attempts made, refreshing...");
				this.webClient.close();
				this.webClient = new WebClient(BrowserVersion.FIREFOX_60);
				webClient.getOptions().setJavaScriptEnabled(true); 
				webClient.getCookieManager().setCookiesEnabled(true);
				webClient.getOptions().setRedirectEnabled(true);
				System.out.println("Relogging...");
				System.out.println(login(this.username, this.password) ? "Relogged": "failed to relog");
				this.page = webClient.getPage(url);
				System.out.println("-----Previous---Xml");
				System.out.println(contentSection.asXml());
				System.out.println("\n Getting new content...");
				page = this.webClient.getPage(url);
				Thread.sleep(5000);
				contentSection = page.querySelector("ul#mt-sectioncontainer");
				System.out.println("-----Current---Xml");
				System.out.println(contentSection.asXml());
				attempts = 1;
			}
		}
		return contentSection;
	}
	

	
	private void downloadFile(String downloadLink, String path) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		// Idea here is to determine the type of file you handle, 
		System.out.println("Handling: " + downloadLink);
		WebResponse response = null;
		try {
			response = this.webClient.getPage(downloadLink).getWebResponse();
		} catch(Exception e) {
			System.out.println("Link unrelated to Moodle");
			return; // if a webresponse gets an exception, it is likely a page unrelated to moodle
		}
		
		
		if(response.getContentType().equals("text/html")) {
			HtmlPage downloadPage = null;
			// somtimes the Webclient complains, still not sure why
			try {
				downloadPage = this.webClient.getPage(downloadLink);
			} catch (Exception e) {
				System.out.println("Error parsing downloadlink.");
				return;
			}
			
			// we need to figure out what kind of page it is
			// a big clue here is the body, the id of the body gives away what kind of files it shows
			switch (((DomElement)downloadPage.querySelector("body")).getAttribute("id")) {
			case "page-mod-resource-view":
				// there are two possible files that have this body id: pdf preview, and redirect download, so check for both
				if (downloadPage.querySelector("iframe#resourceobject") != null) {
					downloadFromRedirect(downloadPage, path);
				}
				else if (downloadPage.querySelector("object#resourceobject") != null) {
					downloadPdfPreview(downloadPage, path);
				}
				else {
					// probably an old form of gettin the document
					DomElement link = ((DomElement)downloadPage.querySelector("div[role=main]")).querySelector("a");
					if(link != null) {
						downloadFromWebResponse(webClient.getPage(link.getAttribute("href")).getWebResponse(), path);
					}
					else {
						// but if not, i'd be confused
						System.out.println("wtf");
					}
				}
				break;			
			case "page-mod-forum-view":
				downloadTextFile(downloadPage, path);
				break;
			case "page-mod-page-view":
				downloadTextFile(downloadPage, path);
				break;
			case "page-mod-folder-view":
				downloadMapFolder(downloadPage, path);
				break;
			case "page-mod-assign-view":
				
				break;
			case "page-mod-questionnaire-view":
				
				break;
				
			default:
				// Could be a page unrelated to Moodle, but print it out anyway if available
				System.out.println("Unknown file referal: " + ((DomElement)downloadPage.querySelector("body")).getAttribute("id"));
				
			}
		}
		else {
			downloadFromWebResponse(response, path); // If its not a html page, it's a direct download where we can get the content from
		}
		
	}
	private void downloadFromWebResponse(WebResponse response, String path) throws IOException {
		// this method takes the webresponse, gets the name and inputstream and write it to the path
		String filename = response.getResponseHeaderValue("Content-Disposition");
		filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
		System.out.println("Downloading: " + filename);
		try (InputStream in = response.getContentAsStream()) {
            try (FileOutputStream out = new FileOutputStream(path + "/" + filename)) {
                byte[] buffer = new byte[8 * 1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
            System.out.println(filename + " succesfully downloaded.");
        }
	}
	private void downloadPdfPreview(HtmlPage page, String path) throws IOException {
		// When moodle gives a pdf preview page, it hides the source in a object tag, 
		DomElement resource = (DomElement)page.querySelector("object#resourceobject");
		downloadFromWebResponse(webClient.getPage(resource.getAttribute("data")).getWebResponse(), path);
	}
	
	private void downloadTextFile(HtmlPage page, String path) throws IOException {
		// needs to be better, naming and such
		String pageContent = page.querySelector("div[role=main]").asText();
		String title = page.querySelector("h2").asText();
		title = removeIllegalCharsFromFileName(title);
		//System.out.println(pageContent);
		Files.write(Paths.get(path + "/" + title + ".txt"), pageContent.getBytes());
		System.out.println("Downloaded TextFile as: " + title + ".txt");
	}
	private void downloadMapFolder(HtmlPage page, String path) throws IOException {
		DomElement content = page.querySelector("section#region-main");
		DomElement form = content.querySelector("form");
		System.out.println(form.asXml());
	    var element = (DomElement)form.querySelector("button");
	    if(element == null) {
	    	downloadTextFile(page, path);
	    }
	    else {		
	    	downloadFromWebResponse(element.click().getWebResponse(), path);
		}
		
	}
	private void downloadFromRedirect(HtmlPage page, String path) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
		String urlString = ((DomElement)page.querySelector("iframe#resourceobject")).getAttribute("src");
		downloadFromWebResponse(this.webClient.getPage(urlString).getWebResponse(), path);
	}
	private String removeIllegalCharsFromFileName(String filename) {
		return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
	}
	
}
