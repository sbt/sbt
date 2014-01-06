package xsbti;

import java.io.File;

/**
 * A launched application returns an instance of this class in order to communicate to the launcher
 * that the application should be restarted. Different versions of the application and Scala can be used.
 * The application can be given different arguments as well as a new working directory.
 */
public interface Reboot extends MainResult
{
	public String[] arguments();
	public File baseDirectory();
	public String scalaVersion();
	public ApplicationID app();
}