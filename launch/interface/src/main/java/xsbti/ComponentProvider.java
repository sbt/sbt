package xsbti;

import java.io.File;

public interface ComponentProvider
{
	public File componentLocation(String id);
	public File[] component(String componentID);
	public void defineComponent(String componentID, File[] components);
	public boolean addToComponent(String componentID, File[] components);
	 // null if locking disabled
	public File lockFile();
}