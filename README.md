Deprecated console application! Visit the new and improved MAFR Client and MAFR Server repositories

[MAFR CLIENT](https://github.com/Rascalov/MafrClientSide)

[MAFR SERVER](https://github.com/Rascalov/MafrServerSide)


![MafrLogo](https://i.imgur.com/j031Zng.png)

Console Course downloader for the Inholland Moodle  Environment 

# How to use the command line arguments
If you want to run Mafr manually, you can just run it as
```
java -jar mafr1.5.jar
```
To put the application in updatemode (beta functionality), use the following:
```
java -jar mafr1.5.jar -u MoodleCourseUrl
```
You can also start the application automatically for a certain course by logging in automatically and specifying the directory. 

Recommended use is in a bash/shell script which you would run periodically
```
java -jar mafr1.5.jar -u MoodleCourseUrl -login username password -d D:\a\path\to\the\course\folder
```
Note: The MoodleCourseUrl has to be clean with only its id ( e.g. https://moodle.inholland.nl/course/view.php?id=10558 )

# Build
Either download the jar (includes dependencies)

Or you build it with maven on the console
```
 mvn clean compile install package assembly:single
```


TODO:

- **Better Link crawler, instead of only looking for links, look for videos, text etc.**

- **Graphical user interface version**

- **Automatically sync files added to moodle to your computer**
    Beta version out in 1.5

- **Asynchronous downloading**

--- more to be added ---


# How to make a better Crawler

The current crawler leaves a lot to be desired when it comes to content crawling.

**How it works now**

Currently, for each menu section, Mafr simply takes all 'a href' elements and indexes them. After the index, Mafr handles each link grabbed, if its webresponse can be determined, Mafr downloads it.

This works in most use cases, but Moodle courses are starting to use embedded functionalities that no longer rely on links and get displayed directly on the menu section. 

These include, but are not limited to: videos, text, and images. 

Text is a unique challenge on its own, as it is not a file, but merely a big string, it has no Last-Modified or other HTTP headers. Unlike videos and Images. 

**What might be possible**

Moodle Content is displayed in ul > li tag style 

Example below:

![MoodleStructure](https://i.imgur.com/qZ7rV0D.png)

So, for each li in this structure, we could use a method that determines what the li has, and what must be indexed for downloading. 

If there is text, it might be prudent to download it immediately, as it cannot be indexed as a link. 

li tags have certain classes that define what their content consumes.

Current found classes and their definitions:
- **modtype_resource: holds one(?) link to a resource**
- **modtype_label: holds text without resource links, though could include links. Usually used for Header Titles**
- **modtype_book: Holds a video(?) in a seperate window**
- **modtype_folder: Holds a folder which can include multiple files, in multiple trees even**
- **modtype_forum: Holds a forum which has multiple announcements from the course**
- **modtype_assign: Holds an assignment field where users can drop assignments, probably never interesting** 
- **modtype_page: Holds a text file, some have a Last-Modified in string format**  
- **modtype_url: Holds url should be saved as a textfile**   
- **modtype_groupselect: Holds a list of group members, can be saved as textfile**   


Every mod type can have text, no matter what they convey (save for modtype_label, that's its entire purpose)

For every li there should be a text file, the text file would have the name of either the resource it is associated with OR, in case it has none of that, its first p tag textcontent will be the filename.

After a Title (h3), its possible for there to be a "summary" class, which contains text about the folder. The title of these textfiles should be equal to the title of which they summarize (so, the h3 tag).

modtype_label has a class "contentwithoutlink", which holds all the text. You need to get all the text, but also look voor ahref tags, if they embed them on a word, you have to seperate that, if not, it will get stored in the text file without worries. 


Other modtypes have divs that hold the text **before** (NOT SEEN YET, BUT COULD EXIST) and **after** the resource link.

The after class is "contentafterlink", its title should be the "TEXT" + resource_name text value. 











