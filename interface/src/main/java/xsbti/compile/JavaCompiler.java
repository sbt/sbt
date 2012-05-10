package xsbti.compile;

import java.io.File;
import xsbti.Logger;

/** 
* Interface to a Java compiler.
*/
public interface JavaCompiler
{
	/** Compiles Java sources using the provided classpath, output directory, and additional options.
	* If supported, the number of reported errors should be limited to `maximumErrors`.
	* Output should be sent to the provided logger.*/
	void compile(File[] sources, File[] classpath, File outputDirectory, String[] options, int maximumErrors, Logger log);
}
