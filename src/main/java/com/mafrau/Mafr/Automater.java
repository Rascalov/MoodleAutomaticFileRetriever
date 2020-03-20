package com.mafrau.Mafr;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.DomNodeList;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlEmailInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import org.apache.commons.lang3.StringUtils;

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
		System.out.println(page.asXml());
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
		System.out.println("Map Structure determined. Fetching clean url...");
		// now, foreach folder structure, we call
		String cleanurl = getCleanUrl();
		System.out.println("Clean url fetched: " + cleanurl);
		File f;
		ArrayList<MoodleFile> rawLinkList = new ArrayList<MoodleFile>();
		System.out.println("Downloading Course Subjects...");
		for(MoodleFolder folder : structure){
			f = new File(folder.path);
			f.mkdir();
			rawLinkList.addAll(getMoodleFilesByFolder(folder, cleanurl));
		}

		for (MoodleFile file : rawLinkList){
			downloader.downloadFile(file.getDownloadLink(), file.getLocation().path, false);
		}
		System.out.println("Finished Downloading");
		webClient.close();
	}

	public ArrayList<MoodleFolder> determineMapStructure(DomNode menu) {
		// Determine the map structure based on the given menu. We also are able to get the data-sections the menu has.
		// The courses I collected did not go further than a 4 layer course system, maybe it is the limit
		// TODO Rework the method of finding submenus, is is awful atm
		ArrayList<MoodleFolder> MoodleFolders = new ArrayList<MoodleFolder>();
		for (var element : menu.getChildren()) { // foreach Course Subject basically
			String sectionID = ((DomElement)element.querySelector("a.mt-heading-btn")).getAttribute("data-section");
			MoodleFolder headFolder = new MoodleFolder(element.asText(), Integer.parseInt(sectionID) );
			headFolder.path = this.downloadPath + "/" + headFolder.name;
			MoodleFolders.add(headFolder);
			// need to check for sub menu and gather its folders
			MoodleFolder folder;
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
											folder = new MoodleFolder(subsubsubNode.getTextContent(), dataSectionId);
											folder.path = headFolder.path + "/" + folder.name;
											MoodleFolders.add(folder);
										}
									}
									else {
										int dataSectionId = Integer.parseInt(((DomElement)subsubNode).getAttribute("data-section"));
										folder = new MoodleFolder(subsubNode.getTextContent(), dataSectionId);
										folder.path = headFolder.path + "/" + folder.name;
										MoodleFolders.add(folder);
									}
								}
							}
							else {
								int dataSectionId = Integer.parseInt(((DomElement)subNode).getAttribute("data-section"));
								folder = new MoodleFolder(subNode.getTextContent(), dataSectionId);
								folder.path = headFolder.path + "/" + folder.name;
								MoodleFolders.add(folder);
							}
						}
					}
					else {
						int dataSectionId = Integer.parseInt(((DomElement)node).getAttribute("data-section"));
						folder = new MoodleFolder(node.getTextContent(), dataSectionId);
						folder.path = headFolder.path + "/" + folder.name;
						MoodleFolders.add(folder);
					}
				}
			}
		}
		return MoodleFolders;
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
	private DomNode getContent(String hashJs) throws InterruptedException, FailingHttpStatusCodeException, MalformedURLException, IOException {
		// This returns the current content displayed on the right side
		//this.page = this.webClient.getPage(url);
		try {
			page.executeJavaScript(hashJs);
		}
		catch (Exception e){
			System.out.println("Javascript Tick error, trying workaround...");
			// weird errors can occur, in which case we manually head to a page with the correct hash location
			page = webClient.getPage((getCleanUrl() + "#" + StringUtils.substring(hashJs, '\'', '\'')));
		}
		DomNode contentSection = page.querySelector("ul#mt-sectioncontainer");
		int attempts = 1;
		while (contentSection.asText().equals("")) {
			// it can take some time for content to be displayed, so wait untill it does
			System.out.println("Failed to get content. Trying again.. attempt: " +  attempts);
			Thread.sleep(200);
			attempts++;
		}
		System.out.println("Content Received!");
		return contentSection;
	}
	private void Reset(){
		// TODO Determine further use. As recent optimization has resulted in this method no longer being necessary
		this.webClient.close();
		this.webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
		webClient.getOptions().setJavaScriptEnabled(true);
		webClient.getCookieManager().setCookiesEnabled(true);
		webClient.getOptions().setRedirectEnabled(true);
	}
	private String removeIllegalCharsFromFileName(String filename) {
		return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
	}

	public void updateFiles() throws IOException, InterruptedException {
		// We download new/updated files as soon as they become available to the account you logged on to
		// this can be called separately or after a mass download
		// We gather two MoodleFile lists
		// 1st list is a uncurated list, all <a> elements found
		// 2nd list will be made by looping (maybe)

		System.out.println("Updating the Course...");
		page = webClient.getPage(moodleCourseUrl);
		DomElement menu = page.querySelector("div.mt-sitemenus");
		// We take the structure again
		System.out.println("Getting Map structure");
		var structure = determineMapStructure(menu);
		System.out.println("Map Structure recieved");
		String cleanurl = getCleanUrl();
		System.out.println("Fetching files...");
		ArrayList<MoodleFile> files = new ArrayList<MoodleFile>();
		// Loop through the folders, take their path, if it doesn't exist, make it
		for(MoodleFolder moodleFolder : structure){
			System.out.println("Getting content for " + moodleFolder.name);
			File folder = new File(moodleFolder.path);
			if(!folder.exists()){
				folder.mkdir();
			}
			files.addAll(getMoodleFilesByFolder(moodleFolder, cleanurl));
		}
		// now we have all potential moodlefiles
		// Now we must sanitize/discover files (maps hide additional files)


		// after we can loop over them, and decide whether we download them or not.

		for(MoodleFile file : files){
			// Determine whether it can and/or must be downloaded by setting the downloadFilemethod to updateMode
			downloader.downloadFile(file.getDownloadLink(), file.getLocation().path, true);
		}
		webClient.close();
	}
	private ArrayList<MoodleFile> getMoodleFilesByFolder(MoodleFolder folder, String cleanCourseUrl) throws IOException, InterruptedException {
		ArrayList<MoodleFile> moodleFiles = new ArrayList<MoodleFile>();
		String js = "window.location.hash = 'section-" + folder.sectionId + "'";
		DomNode content  = getContent(js);
		DomNodeList<DomNode> downloadLinks = content.querySelectorAll("a");
		for (DomNode initLink : downloadLinks) {
			if(!((DomElement)initLink.getParentNode()).getTagName().equals("h3")) {
				System.out.println("Name: " + initLink.asText());
				System.out.println("Resource Link: " + ((DomElement)initLink).getAttribute("href") );
				MoodleFile moodleFile = new MoodleFile(((DomElement)initLink).getAttribute("href"), folder);
				moodleFiles.add(moodleFile);
			}
		}
		return moodleFiles;
	}
}
