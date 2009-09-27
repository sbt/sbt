package xsbti;

import java.io.File;

public interface ComponentProvider
{
	public File[] component(String componentID);
	public void defineComponent(String componentID, File[] components);
}