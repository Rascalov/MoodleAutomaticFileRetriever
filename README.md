![MafrLogo](https://i.imgur.com/j031Zng.png)

Console Course downloader for the Inholland Moodle  Environment 

# How to use the command line arguments
If you want to run Mafr regularly, you can just run it as
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
