/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

import java.io.File;
import java.util.Optional;

public interface Position
{
	Optional<Integer> line();
	String lineContent();
	Optional<Integer> offset();

	// pointer to the column position of the error/warning
	Optional<Integer> pointer();
	Optional<String> pointerSpace();

	Optional<String> sourcePath();
	Optional<File> sourceFile();

	// Default values to avoid breaking binary compatibility
	default Optional<Integer> startOffset() { return Optional.empty(); }
	default Optional<Integer> endOffset() { return Optional.empty(); }
}
