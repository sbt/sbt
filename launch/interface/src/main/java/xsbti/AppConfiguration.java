package xsbti;

import java.io.File;

public interface AppConfiguration
{
	public String[] arguments();
	public File baseDirectory();
	public AppProvider provider();
}