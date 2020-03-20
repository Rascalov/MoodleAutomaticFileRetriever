package com.mafrau.Mafr;

public class MoodleFolder {
	public String name;
	public int sectionId;
	public String path; // decided to throw it in where necessary, it will be filled in the iteration on the automator
	public MoodleFolder(String foldername, int sectionId) {
		this.name = foldername.replaceAll("[^a-zA-Z0-9\\.\\-]", "-"); // prevents use of chars that break folder making 
		this.sectionId = sectionId; // section id determines the url required to force js to show appropriate content
	}
}
