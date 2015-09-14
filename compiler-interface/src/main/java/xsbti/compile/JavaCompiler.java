package xsbti.compile;

import java.io.File;
import xsbti.Logger;
import xsbti.Reporter;

/**
* Interface to a Java compiler.
*/
public interface JavaCompiler
{
    /**
     * Compiles java sources using the provided classpath, output directory and additional options.
     *
     * Output should be sent to the provided logger.
     * Failures should be passed to the provided Reporter.
     */
    void compileWithReporter(File[] sources, File[] classpath, Output output, String[] options, Reporter reporter, Logger log);
}
