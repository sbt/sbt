package sbt.internal.scripted;

import java.io.File;

import xsbti.Logger;

public class ScriptConfig {

	private String label;
	private File testDirectory;
	private Logger logger;

	public ScriptConfig(String label, File testDirectory, Logger logger) {
		this.label = label;
		this.testDirectory = testDirectory;
		this.logger = logger;
	}

	public String label() {
		return this.label;
	}

	public File testDirectory() {
		return this.testDirectory;
	}

	public Logger logger() {
		return this.logger;
	}

}