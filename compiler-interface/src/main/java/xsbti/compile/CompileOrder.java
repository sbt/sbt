package xsbti.compile;

/** 
* Defines the order in which Scala and Java sources are compiled when compiling a set of sources with both Java and Scala sources.
* This setting has no effect if only Java sources or only Scala sources are being compiled.
* It is generally more efficient to use JavaThenScala or ScalaThenJava when mixed compilation is not required.
*/
public enum CompileOrder
{
	/**
	* Allows Scala sources to depend on Java sources and allows Java sources to depend on Scala sources.
	* 
	* In this mode, both Java and Scala sources are passed to the Scala compiler, which generates class files for the Scala sources.
	* Then, Java sources are passed to the Java compiler, which generates class files for the Java sources.
	* The Scala classes compiled in the first step are included on the classpath to the Java compiler.
	*/
	Mixed,
	/**
	*  Allows Scala sources to depend on the Java sources in the compilation, but does not allow Java sources to depend on Scala sources.
	*
	* In this mode, both Java and Scala sources are passed to the Scala compiler, which generates class files for the Scala sources.
	* Then, Java sources are passed to the Java compiler, which generates class files for the Java sources.
	* The Scala classes compiled in the first step are included on the classpath to the Java compiler.
	*/
	JavaThenScala,
	/**
	*  Allows Java sources to depend on the Scala sources in the compilation, but does not allow Scala sources to depend on Java sources.
	*
	* In this mode, both Java and Scala sources are passed to the Scala compiler, which generates class files for the Scala sources.
	* Then, Java sources are passed to the Java compiler, which generates class files for the Java sources.
	* The Scala classes compiled in the first step are included on the classpath to the Java compiler.
	*/
	ScalaThenJava
}