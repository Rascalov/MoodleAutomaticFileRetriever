package com.mafrau.Mafr;

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MoodleDownloader {
    // Goal is to split downloading into a seperate class, to give Updater class access to the created download methods without relying on Automater
    private WebClient webClient;
    public MoodleDownloader(WebClient webClient){
        this.webClient = webClient;
    }

    public ArrayList<MoodleFile> getFileinfo(String downloadLink, MoodleFolder destination) throws IOException {
        ArrayList<MoodleFile> files = new ArrayList<MoodleFile>();
        WebResponse response = null;
        if(isMoodleLink(downloadLink)){
            response = this.webClient.getPage(downloadLink).getWebResponse();
        }
        else {
            return null;
        }

        if(response.getContentType().equals("text/html")) {
            HtmlPage downloadPage = null;
            // somtimes the Webclient complains, still not sure why
            try {
                downloadPage = this.webClient.getPage(downloadLink);
            } catch (Exception e) {
                System.out.println("Error parsing downloadlink.");
                return null;
            }

            MoodleFile file =null;
            WebResponse r;
            String filename;
            int filesize;
            String LastModified;
            // we need to figure out what kind of page it is
            // a big clue here is the body, the id of the body gives away what kind of files it shows
            switch (((DomElement)downloadPage.querySelector("body")).getAttribute("id")) {
                case "page-mod-resource-view":
                    // there are two possible files that have this body id: pdf preview, and redirect download, so check for both
                    if (downloadPage.querySelector("iframe#resourceobject") != null) {
                        r = responseFromRedirect(downloadPage);
                        filename = r.getResponseHeaderValue("Content-Disposition");
                        filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                        filesize = Integer.parseInt(r.getResponseHeaderValue("Content-Length"));
                        LastModified = r.getResponseHeaderValue("Last-Modified");
                        //file = new MoodleFile(filename ,downloadLink, filesize, destination, LastModified);
                        files.add(file);
                    }
                    else if (downloadPage.querySelector("object#resourceobject") != null) {
                        r = responseFromPdfPreview(downloadPage);
                        filename = r.getResponseHeaderValue("Content-Disposition");
                        filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                        filesize = Integer.parseInt(r.getResponseHeaderValue("Content-Length"));
                        LastModified = r.getResponseHeaderValue("Last-Modified");
                        //file = new MoodleFile(filename ,downloadLink, filesize, destination, LastModified);
                        files.add(file);
                    }
                    else {
                        // probably an old form of gettin the document
                        DomElement link = ((DomElement)downloadPage.querySelector("div[role=main]")).querySelector("a");
                        if(link != null) {
                            r = webClient.getPage(link.getAttribute("href")).getWebResponse();
                            filename = r.getResponseHeaderValue("Content-Disposition");
                            filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                            filesize = Integer.parseInt(r.getResponseHeaderValue("Content-Length"));
                            LastModified = r.getResponseHeaderValue("Last-Modified");
                            //  file = new MoodleFile(filename ,downloadLink, filesize, destination, LastModified);
                            files.add(file);
                        }
                        else {
                            // but if not, i'd be confused
                            System.out.println("weird");
                        }
                    }
                    break;
                case "page-mod-forum-view":
                case "page-mod-page-view":
                    downloadTextFile(downloadPage, destination.path);
                    break;
                case "page-mod-folder-view":
                    String mapTitle =  removeIllegalCharsFromFileName(downloadPage.querySelector("#region-main").querySelector("h2").getTextContent());
                    new File(destination.path.substring(0, destination.path.lastIndexOf('/')) + "/" + mapTitle).mkdir();
                    for(WebResponse webResponse : responseFromMapFolder(downloadPage, destination.path)){
                        r = webResponse;
                        filename = r.getResponseHeaderValue("Content-Disposition");
                        filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                        filesize = Integer.parseInt(r.getResponseHeaderValue("Content-Length"));
                        LastModified = r.getResponseHeaderValue("Last-Modified");
                        MoodleFolder tempfolder = new MoodleFolder(mapTitle,0);
                        tempfolder.path = destination.path + "/" + mapTitle;
                        //file = new MoodleFile(filename, downloadLink, filesize, tempfolder, LastModified);
                        files.add(file);
                        //downloadFromWebResponse(webResponse, destination.path+ "/" + mapTitle);
                    }
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
            // If its not a html page, it's a direct download where we can get the content from

            String filename = response.getResponseHeaderValue("Content-Disposition");
            filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
            int filesize = Integer.parseInt(response.getResponseHeaderValue("Content-Length"));
            String LastModified = response.getResponseHeaderValue("Last-Modified");
            //MoodleFile file = new MoodleFile(filename, downloadLink, filesize, destination, LastModified);
            //files.add(file);
        }

        return files;
    }
    private boolean isMoodleLink(String downloadLink){
        WebResponse response = null;
        try {
            response = this.webClient.getPage(downloadLink).getWebResponse();
        } catch(Exception e) {
            System.out.println("Link unrelated to Moodle");
            return false; // if a webresponse gets an exception, it is likely a page unrelated to moodle
        }
        return true;
    }
    public void downloadFile(String downloadLink, String path, boolean updateMode) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
        // Idea here is to determine the type of file you handle,
        System.out.println("Handling: " + downloadLink);
        WebResponse response = null;
        WebResponse downloadResponse = null;
        if(isMoodleLink(downloadLink)){
            response = this.webClient.getPage(downloadLink).getWebResponse();
        }
        else {
            System.out.println("No moodle download link");
            return;
        }

        if(response.getContentType().equals("text/html")) {
            HtmlPage downloadPage = null;
            // sometimes the Webclient complains, still not sure why
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
                        downloadResponse = responseFromRedirect(downloadPage);
                    }
                    else if (downloadPage.querySelector("object#resourceobject") != null) {
                        downloadResponse = responseFromPdfPreview(downloadPage);
                    }
                    else {
                        // probably an old form of gettin the document
                        DomElement link = ((DomElement)downloadPage.querySelector("div[role=main]")).querySelector("a");
                        if(link != null) {
                            downloadResponse = webClient.getPage(link.getAttribute("href")).getWebResponse();
                            //downloadFromWebResponse(webClient.getPage(link.getAttribute("href")).getWebResponse(), path);
                        }
                        else {
                            // but if not, i'd be confused
                            System.out.println("weird");
                        }
                    }
                    break;
                case "page-mod-forum-view":
                case "page-mod-page-view":
                    // not much going for it, most don't have a last modified date anywhere, so we have to download it always just to be safe
                    // TODO find a way to reliably get Last-Modified Long value for generated text files.
                    downloadTextFile(downloadPage, path);
                    break;
                case "page-mod-folder-view": // Folders are a bit tricky, but doable for downloading and updating
                    String mapTitle =  removeIllegalCharsFromFileName(downloadPage.querySelector("#region-main").querySelector("h2").getTextContent());
                    new File(path + "/" + mapTitle).mkdir();
                    for(WebResponse webResponse : responseFromMapFolder(downloadPage, path)){
                        if(updateMode){ //
                            String filename = webResponse.getResponseHeaderValue("Content-Disposition");
                            filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                            File file = new File(path + "/" + mapTitle + "/" +  filename);
                            if(file.exists()){
                                if(MoodleFile.httpDateTimeHeaderToMs(webResponse.getResponseHeaderValue("Last-Modified")) > file.lastModified()){
                                    downloadFromWebResponse(webResponse, path + "/" + mapTitle);
                                }
                            }
                            else {
                                downloadFromWebResponse(webResponse, path + "/" + mapTitle);
                            }
                        }
                        else {
                            downloadFromWebResponse(webResponse, path + "/" + mapTitle);
                        }
                    }
                    break;
                case "page-mod-assign-view":
                case "page-mod-questionnaire-view":
                    break;
                case "page-mod-book-view":
                    downloadResponse = responseFrombookview(downloadPage);
                    break;
                default:
                    // Could be a page unrelated to Moodle, but print it out anyway if available
                    System.out.println("Unknown file referal: " + ((DomElement)downloadPage.querySelector("body")).getAttribute("id"));

            }
            if(downloadResponse != null){
                if(updateMode){
                    String filename = downloadResponse.getResponseHeaderValue("Content-Disposition");
                    filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                    File file = new File(path + "/" +  filename);
                    if(file.exists()){
                        if(MoodleFile.httpDateTimeHeaderToMs(downloadResponse.getResponseHeaderValue("Last-Modified")) > file.lastModified()){
                            downloadFromWebResponse(downloadResponse, path);
                        }
                    }
                    else {
                        downloadFromWebResponse(downloadResponse, path);
                    }
                }
                else {
                    downloadFromWebResponse(downloadResponse, path);
                }
            }

        }
        else { // If its not a html page, it's a direct download where we can get the content from
            if(updateMode){ // if we're updating, we need to see if it needs to be downloaded
                String filename = response.getResponseHeaderValue("Content-Disposition");
                filename = StringUtils.substringsBetween(filename, "\"", "\"")[0];
                File file = new File(path + "/" +  filename);
                if(file.exists()){
                    if(MoodleFile.httpDateTimeHeaderToMs(response.getResponseHeaderValue("Last-Modified")) > file.lastModified()){
                        downloadFromWebResponse(response, path);
                    }
                }
                else {
                    downloadFromWebResponse(response, path);
                }
            }
            else {
                downloadFromWebResponse(response, path);
            }
        }

    }
    private void downloadFromWebResponse(WebResponse response, String path) throws IOException {
        // this method takes the webresponse, gets the name and inputstream and write it to the pat
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
    private WebResponse responseFromPdfPreview(HtmlPage page) throws IOException {
        // When moodle gives a pdf preview page, it hides the source in a object tag,
        DomElement resource = (DomElement)page.querySelector("object#resourceobject");
        return webClient.getPage(resource.getAttribute("data")).getWebResponse();
        //downloadFromWebResponse(webClient.getPage(resource.getAttribute("data")).getWebResponse(), path);
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
    private ArrayList<WebResponse>  responseFromMapFolder(HtmlPage page, String path) throws IOException {
        // Meant to download Moodle map structures as zip or textfile depending on the content
        DomElement content = page.querySelector("section#region-main");
        DomElement form = content.querySelector("form");
        var element = (DomElement)form.querySelector("button");
        if(element == null) {
            downloadTextFile(page, path);
            return null;
        }
        else {
            ArrayList<WebResponse> responses = new ArrayList<WebResponse>();
            DomElement tree = page.querySelector("#folder_tree0");
            for(var link : tree.querySelectorAll("a")){
                try{
                    responses.add(webClient.getPage(((DomElement)link).getAttribute("href")).getWebResponse());
                }
                catch (Exception e){
                }
            }
            return  responses;
        }

    }
    private WebResponse responseFromRedirect(HtmlPage page) throws FailingHttpStatusCodeException, MalformedURLException, IOException {
        String urlString = ((DomElement)page.querySelector("iframe#resourceobject")).getAttribute("src");
        return this.webClient.getPage(urlString).getWebResponse();
        //downloadFromWebResponse(this.webClient.getPage(urlString).getWebResponse(), path);
    }
    private WebResponse responseFrombookview(HtmlPage page){
        // current goal: get webresponse for video stored in book-view
        // If there is anything else in view, we will have to test further to see if it needs to be handled like map folders
        try {
            DomElement section = (DomElement)page.querySelector("section#region-main");
            DomElement video = (DomElement)section.querySelector("video");
            DomElement source = (DomElement)video.querySelector("source");
            return webClient.getPage(source.getAttribute("src")).getWebResponse();
        }
        catch (Exception e){
            System.out.println("File in book-view is not a video, to fix this, report it to the the MAFR creators and provide a link to the course and the link to the resource");
            e.printStackTrace();
            return null;
        }


    }
    private String removeIllegalCharsFromFileName(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    }
}
