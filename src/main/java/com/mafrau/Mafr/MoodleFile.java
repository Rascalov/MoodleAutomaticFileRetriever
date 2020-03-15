package com.mafrau.Mafr;

public class MoodleFile {
    public String name, downloadLink;
    public int size;
    public MoodleFolder location;

    public MoodleFile(String name, String downloadLink, int size, MoodleFolder location){
        this.name = name;
        this.downloadLink = downloadLink;
        this.size = size;
        this.location = location;
    }
}
