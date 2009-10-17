package xsbti;

import java.io.File;

/** Provides access to the jars and classes for a particular version of Scala.*/
public interface ScalaProvider
{
	public Launcher launcher();
	/** The version of Scala this instance provides.*/
	public String version();

	/** A ClassLoader that loads the classes from scala-library.jar and scala-compiler.jar.*/
	public ClassLoader loader();
	/** Returns the scala-library.jar and scala-compiler.jar for this version of Scala. */
	public File[] jars();
	public File libraryJar();
	public File compilerJar();
	/** Creates an application provider that will use 'loader()' as the parent ClassLoader for
	* the application given by 'id'.  This method will retrieve the application if it has not already
	* been retrieved.*/
	public AppProvider app(ApplicationID id);
}