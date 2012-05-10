package xsbti.compile;

import xsbti.Logger;
import xsbti.Reporter;

public interface GlobalsCache
{
	public CachedCompiler apply(String[] args, boolean forceNew, CachedCompilerProvider provider, Logger log, Reporter reporter);
	public void clear();
}
