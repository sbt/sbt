package xsbti;

public interface ApplicationID
{
	public String groupID();
	public String name();
	public String version();

	public String mainClass();
	public String[] mainComponents();
	public boolean crossVersioned();
}