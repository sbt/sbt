package xsbti;

import java.io.File;

public interface ApplicationID
{
	public String groupID();
	public String name();
	public String version();

	public String mainClass();
	public String[] mainComponents();
	public boolean crossVersioned();
	public CrossValue crossVersionedValue();
	
	/** Files to add to the application classpath. */
	public File[] classpathExtra();
}