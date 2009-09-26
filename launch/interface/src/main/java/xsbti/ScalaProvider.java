package xsbti;

import java.io.File;

public interface ScalaProvider
{
	public Launcher launcher();
	public String version();

	public ClassLoader loader();
	public File[] jars();
	public AppProvider app(ApplicationID id);
}