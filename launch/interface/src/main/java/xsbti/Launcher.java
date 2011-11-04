package xsbti;

	import java.io.File;

public interface Launcher
{
	public static final int InterfaceVersion = 1;
	public ScalaProvider getScala(String version);
	public ScalaProvider getScala(String version, String reason);
	public ClassLoader topLoader();
	public GlobalLock globalLock();
	public File bootDirectory();
	public xsbti.Repository[] ivyRepositories();
	// null if none set
	public File ivyHome();
	public String[] checksums();
}