package xsbti;

import java.io.File;

public interface AppProvider
{
	public ScalaProvider scalaProvider();
	public ApplicationID id();

	public Class<? extends AppMain> mainClass();
	public AppMain newMain();

	public File[] component(String componentID);
	public void defineComponent(String componentID, File[] components);
}