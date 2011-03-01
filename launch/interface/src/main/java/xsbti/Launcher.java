package xsbti;

	import java.io.File;

public interface Launcher
{
	public static final int InterfaceVersion = 1;
	public ScalaProvider getScala(String version);
	public ClassLoader topLoader();
	public GlobalLock globalLock();
	public File bootDirectory();
	// null if none set
	public File cacheDirectory();
}