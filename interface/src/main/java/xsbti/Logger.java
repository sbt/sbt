/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbti;

public interface Logger
{
	public void error(F0<String> msg);
	public void warn(F0<String> msg);
	public void info(F0<String> msg);
	public void debug(F0<String> msg);
	public void trace(F0<Throwable> exception);
}
