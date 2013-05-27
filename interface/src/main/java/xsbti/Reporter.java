/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

public interface Reporter
{
	/** Resets logging, including any accumulated errors, warnings, messages, and counts.*/
	public void reset();
	/** Returns true if this logger has seen any errors since the last call to reset.*/
	public boolean hasErrors();
	/** Returns true if this logger has seen any warnings since the last call to reset.*/
	public boolean hasWarnings();
	/** Logs a summary of logging since the last reset.*/
	public void printSummary();
	/** Returns a list of warnings and errors since the last reset.*/
	public Problem[] problems();
	/** Logs a message.*/
	public void log(Position pos, String msg, Severity sev);
	/** Reports a comment. */
	public void comment(Position pos, String msg);
}
