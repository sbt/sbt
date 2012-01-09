package xsbti.compile;

public interface Compilers<ScalaCompiler>
{
	JavaCompiler javac();
	// should be cached by client if desired
	ScalaCompiler scalac();
}
