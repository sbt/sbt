package xsbti.compile;

import java.io.File;
import xsbti.Logger;
import xsbti.Reporter;

/** 
* Interface to a Java compiler.
*/
public interface JavaCompiler
{
	/** Compiles Java sources using the provided classpath, output directory, and additional options.
	 * Output should be sent to the provided logger.
     *
     * @deprecated 0.13.8 - Use compileWithReporter instead
     */
	void compile(File[] sources, File[] classpath, Output output, String[] options, Logger log);

    /**
     * Compiles java sources using the provided classpath, output directory and additional options.
     *
     * Output should be sent to the provided logger.
     * Failures should be passed to the provided Reporter.
     */
    void compileWithReporter(File[] sources, File[] classpath, Output output, String[] options, Reporter reporter, Logger log);
}