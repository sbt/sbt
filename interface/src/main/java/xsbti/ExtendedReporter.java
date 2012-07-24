/* sbt -- Simple Build Tool
 * Copyright 2012 Eugene Vigdorchik
 */
package xsbti;

/** An addition to standard reporter. Used by the IDE. */
public interface ExtendedReporter extends Reporter
{
	public void comment(Position pos, String msg);
}
