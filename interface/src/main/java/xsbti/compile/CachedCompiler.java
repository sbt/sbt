package xsbti.compile;

import xsbti.AnalysisCallback;
import xsbti.Logger;
import xsbti.Reporter;
import java.io.File;

public interface CachedCompiler
{
	/** Returns an array of arguments representing the nearest command line equivalent of a call to run but without the command name itself.*/
	public String[] commandArguments(File[] sources);
	public void run(File[] sources, DependencyChanges cpChanges, AnalysisCallback callback, Logger log, Reporter delegate, CompileProgress progress);
}
