package xsbti;

import java.io.File;

/**
 * This represents an identification for the sbt launcher to load and run
 * an sbt launched application using ivy.
 */
public interface ApplicationID
{
	/**
	 * @return
	 *   The Ivy orgnaization / Maven groupId where we can find the application to launch.
	 */
	public String groupID();
	/**
	 * @return
	 *    The ivy module name / Maven artifactId where we can find the application to launch.
	 */
	public String name();
	/**
	 * @return
	 *    The ivy/maven version of the module we should resolve.
	 */
	public String version();

	/**
	 * @return
	 *    The fully qualified name of the class that extends xsbti.AppMain
	 */
	public String mainClass();
	/**
	 * @return
	 *    Additional ivy components we should resolve with the main application artifacts.
	 */
	public String[] mainComponents();
	/**
	 * @deprecated
	 * This method is no longer used if the crossVersionedValue method is available.
	 * 
	 * @return
	 *    True if the application is cross-versioned by binary-compatible version string,
	 *    False if there is no cross-versioning.
	 */
	@Deprecated
	public boolean crossVersioned();
	
	/**
	 * 
	 * @since 0.13.0
	 * @return
	 *    The type of cross-versioning the launcher should use to resolve this artifact.
	 */
	public CrossValue crossVersionedValue();
	
	/** Files to add to the application classpath. */
	public File[] classpathExtra();
}