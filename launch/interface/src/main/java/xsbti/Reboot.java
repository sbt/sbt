package xsbti;

import java.io.File;

public interface Reboot extends MainResult
{
	public String[] arguments();
	public File baseDirectory();
	public String scalaVersion();
	public ApplicationID app();
}