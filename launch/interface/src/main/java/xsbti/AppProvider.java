package xsbti;

public interface AppProvider
{
	public ScalaProvider scalaProvider();
	public ApplicationID id();

	public Class<? extends AppMain> mainClass();
	public AppMain newMain();

	public ComponentProvider components();
}
