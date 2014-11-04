package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

/**
 * An interface which lets us know how to retrieve cached compiler instances form the current JVM.
 */
public interface GlobalsCache
{
	public CachedCompiler apply(String[] args, Output output, boolean forceNew, CachedCompilerProvider provider, Logger log, Reporter reporter);
	public void clear();
}
