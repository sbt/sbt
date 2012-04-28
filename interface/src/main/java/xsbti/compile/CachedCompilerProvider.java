package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

public interface CachedCompilerProvider
{
	ScalaInstance scalaInstance();
	CachedCompiler newCachedCompiler(String[] arguments, Logger log, Reporter reporter);
}