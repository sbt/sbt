/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

import java.util.Optional;

public interface Problem
{
	String category();
	Severity severity();
	String message();
	Position position();

  // Default value to avoid breaking binary compatibility
  /**
   * If present, the string shown to the user when displaying this Problem.
   * Otherwise, the Problem will be shown in an implementation-defined way
   * based on the values of its other fields.
   */
  default Optional<String> rendered() { return Optional.empty(); }
}
