package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

public interface CachedCompilerProvider
{
	ScalaInstance scalaInstance();
	CachedCompiler newCachedCompiler(String[] arguments, Output output, Logger log, Reporter reporter, boolean resident);
}