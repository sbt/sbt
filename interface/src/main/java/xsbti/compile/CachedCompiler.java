package xsbti.compile;

import xsbti.AnalysisCallback;
import xsbti.Logger;
import xsbti.Reporter;
import java.io.File;

public interface CachedCompiler
{
	public void run(File[] sources, DependencyChanges cpChanges, AnalysisCallback callback, Logger log, Reporter delegate);
}
