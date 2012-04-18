package xsbti.compile;

/**
* Configures modifications to the classpath based on the Scala instance used for compilation.
* This is typically used for the Scala compiler only and all values set to false for the Java compiler.
*/
public interface ClasspathOptions
{
	/** If true, includes the Scala library on the boot classpath.  This should usually be true.*/
	boolean bootLibrary();

	/** If true, includes the Scala compiler on the standard classpath.
	* This is typically false and is instead managed by the build tool or environment.
	*/
	boolean compiler();

	/** If true, includes extra jars from the Scala instance on the standard classpath.
	* This is typically false and is instead managed by the build tool or environment.
	*/
	boolean extra();

	/** If true, automatically configures the boot classpath.  This should usually be true.*/
	boolean autoBoot();

	/** If true, the Scala library jar is filtered from the standard classpath.
	* This should usually be true because the library should be included on the boot classpath of the Scala compiler and not the standard classpath.
	*/
	boolean filterLibrary();
}