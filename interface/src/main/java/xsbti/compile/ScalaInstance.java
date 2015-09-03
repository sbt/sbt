package xsbti.compile;

import java.io.File;

/**
* Defines Scala instance, which is a reference version String, a unique version String, a set of jars, and a class loader for a Scala version.
*
* Note that in this API a 'jar' can actually be any valid classpath entry.
*/
public interface ScalaInstance
{
	/** The version used to refer to this Scala version.
	* It need not be unique and can be a dynamic version like 2.10.0-SNAPSHOT.
	*/
	String version();

	/** A class loader providing access to the classes and resources in the library and compiler jars. */
	ClassLoader loader();

	/**deprecated Only `jars` can be reliably provided for modularized Scala. (Since 0.13.0) */
	// Deprecated
	File libraryJar();

	/**deprecated Only `jars` can be reliably provided for modularized Scala. (Since 0.13.0) */
	//Deprecated
	File compilerJar();

	/**deprecated Only `jars` can be reliably provided for modularized Scala. (Since 0.13.0) */
	//Deprecated
	File[] otherJars();

	/** All jar files provided by this Scala instance.*/
	File[] allJars();

	/** The unique identifier for this Scala instance.  An implementation should usually obtain this from the compiler.properties file in the compiler jar. */
	String actualVersion();
}
