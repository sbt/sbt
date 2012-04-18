package xsbti.compile;

/** Configures a single compilation of a single set of sources.*/
public interface Inputs<Analysis,ScalaCompiler>
{
	 /** The Scala and Java compilers to use for compilation.*/
	Compilers<ScalaCompiler> compilers();

	/** Standard compilation options, such as the sources and classpath to use. */
	Options options();
 
	/** Configures incremental compilation.*/
	Setup<Analysis> setup();
}
