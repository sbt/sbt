package xsbti.compile;

import java.io.File;
import java.util.Map;

import xsbti.Maybe;
import xsbti.Reporter;

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

	GlobalsCache cache();

	/** If returned, the progress that should be used to report scala compilation to. */
	Maybe<CompileProgress> progress();

	/** The reporter that should be used to report scala compilation to. */
	Reporter reporter();

	/**
	 * Returns incremental compiler options.
	 *
	 * @see sbt.inc.IncOptions for details
	 *
	 * You can get default options by calling <code>sbt.inc.IncOptions.toStringMap(sbt.inc.IncOptions.Default)</code>.
	 *
	 * In the future, we'll extend API in <code>xsbti</code> to provide factory methods that would allow to obtain
	 * defaults values so one can depend on <code>xsbti</code> package only.
	 **/
	Map<String, String> incrementalCompilerOptions();
}
