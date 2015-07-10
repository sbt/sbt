/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
 */
package xsbti;

public interface Logger
{
	void error(F0<String> msg);
	void warn(F0<String> msg);
	void info(F0<String> msg);
	void debug(F0<String> msg);
	void trace(F0<Throwable> exception);
}
