package xsbti;

import java.io.File;

/**
 * This represents an interface that can generate applications or servers.
 * 
 * This provider grants access to launcher related features associated with
 * the id.
 */
public interface AppProvider
{
	/** Returns the ScalaProvider that this AppProvider will use. */
	public ScalaProvider scalaProvider();
	/** The ID of the application that will be created by 'newMain' or 'mainClass'.*/
	public ApplicationID id();
	/** The classloader used to load this application. */
	public ClassLoader loader();
	/** Loads the class for the entry point for the application given by 'id'.  
	 * This method will return the same class every invocation.  
	 * That is, the ClassLoader is not recreated each call.
	 * @deprecated("use entryPoint instead")
	 * 
	 * Note:  This will throw an exception if the launched application does not extend AppMain.
	 */
	@Deprecated
	public Class<? extends AppMain> mainClass();
	/** Loads the class for the entry point for the application given by 'id'.  
	 * This method will return the same class every invocation.  
	 * That is, the ClassLoader is not recreated each call.
	 */
	public Class<?> entryPoint();
	/** Creates a new instance of the entry point of the application given by 'id'.
	* It is NOT guaranteed that newMain().getClass() == mainClass().
	* The sbt launcher can wrap generic static main methods. In this case, there will be a wrapper class,
	* and you must use the `entryPoint` method.
	* @throws IncompatibleClassChangeError if the configuration used for this Application does not
	*                                         represent a launched application.
	*/
	public AppMain newMain();
	
	/** The classpath from which the main class is loaded, excluding Scala jars.*/
	public File[] mainClasspath();

	/** Returns a mechanism you can use to install/find/resolve components.  
	 * A component is just a related group of files.
	 */
	public ComponentProvider components();
}
