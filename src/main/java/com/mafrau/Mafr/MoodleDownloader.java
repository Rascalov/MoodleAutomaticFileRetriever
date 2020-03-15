package com.mafrau.Mafr;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.lang3.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class MoodleDownloader {
    // Goal is to split downloading into a seperate class, to give Updater class access to the created download methods without relying on Automater
    private WebClient webClient;
    public MoodleDownloader(WebClient webClient){
        this.webClient = webClient;
    }

    public void downloadFile(String downloadLink, String path) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
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
                case "page-mod-page-view":
                    downloadTextFile(downloadPage, path);
                    break;
                case "page-mod-folder-view":
                    downloadMapFolder(downloadPage, path);
                    break;
                case "page-mod-assign-view":
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
        // Meant to download Moodle map structures as zip or textfile depending on the content
        DomElement content = page.querySelector("section#region-main");
        DomElement form = content.querySelector("form");
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
