package xsbti.compile;

import java.io.File;
import xsbti.Maybe;

/** Configures incremental recompilation. */
public interface Setup<Analysis>
{
	/** Provides the Analysis for the given classpath entry.*/
	Maybe<Analysis> analysisMap(File file);

	/** Provides a function to determine if classpath entry `file` contains a given class.
	* The returned function should generally cache information about `file`, such as the list of entries in a jar.
	*/
	DefinesClass definesClass(File file);

	/** If true, no sources are actually compiled and the Analysis from the previous compilation is returned.*/
	boolean skip();

	/** The file used to cache information across compilations.
	* This file can be removed to force a full recompilation. 
	* The file should be unique and not shared between compilations. */
	File cacheFile();
}
