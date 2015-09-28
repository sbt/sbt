/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

public interface Reporter
{
	/** Resets logging, including any accumulated errors, warnings, messages, and counts.*/
	void reset();
	/** Returns true if this logger has seen any errors since the last call to reset.*/
	boolean hasErrors();
	/** Returns true if this logger has seen any warnings since the last call to reset.*/
	boolean hasWarnings();
	/** Logs a summary of logging since the last reset.*/
	void printSummary();
	/** Returns a list of warnings and errors since the last reset.*/
	Problem[] problems();
	/** Logs a message.*/
	void log(Position pos, String msg, Severity sev);
	/** Reports a comment. */
	void comment(Position pos, String msg);
}
