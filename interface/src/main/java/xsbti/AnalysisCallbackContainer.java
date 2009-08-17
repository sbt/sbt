/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbti;

/** Provides access to an AnalysisCallback.  This is used by the plugin to
* get the callback to use.  The scalac Global instance it is passed must
* implement this interface. */
public interface AnalysisCallbackContainer
{
	public AnalysisCallback analysisCallback();
}