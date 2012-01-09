package xsbti.compile;

import java.io.File;

/** Standard compilation options.*/
public interface Options
{
	/** The classpath to use for compilation.
	* This will be modified according to the ClasspathOptions used to configure the ScalaCompiler.*/
	File[] classpath();

	/** All sources that should be recompiled.
	* This should include Scala and Java sources, which are identified by their extension. */
	File[] sources();

	/** The directory where class files should be generated.
	* Incremental compilation will manage the class files in this directory.
	* In particular, outdated class files will be deleted before compilation.
	* It is important that this directory is exclusively used for one set of sources. */
	File classesDirectory();

	/** The options to pass to the Scala compiler other than the sources and classpath to use. */
	String[] options();

	/** The options to pass to the Java compiler other than the sources and classpath to use. */
	String[] javacOptions();

	/** The maximum number of errors that the Scala compiler should report.*/
	int maxErrors();

	/** Controls the order in which Java and Scala sources are compiled.*/
	CompileOrder order();
}
