package xsbti;

	import java.io.File;

public interface Launcher
{
	public static final int InterfaceVersion = 1;
	public ScalaProvider getScala(String version);
	public ScalaProvider getScala(String version, String reason);
	public ScalaProvider getScala(String version, String reason, String scalaOrg);
	public AppProvider app(ApplicationID id, String version);
	public ClassLoader topLoader();
	public GlobalLock globalLock();
	public File bootDirectory();
  /** Configured launcher repositories. */
	public xsbti.Repository[] ivyRepositories();
	/** The user has configured the launcher with the only repositories it wants to use for this applciation. */
	public boolean isOverrideRepositories();
	// null if none set
	public File ivyHome();
	public String[] checksums();
}
