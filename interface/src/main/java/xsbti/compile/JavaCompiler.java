package xsbti.compile;

import java.io.File;
import xsbti.Logger;

/** 
* Interface to a Java compiler.
*/
public interface JavaCompiler
{
	/** Compiles Java sources using the provided classpath, output directory, and additional options.
	* Output should be sent to the provided logger.*/
	void compile(File[] sources, File[] classpath, Output output, String[] options, Logger log);
}
