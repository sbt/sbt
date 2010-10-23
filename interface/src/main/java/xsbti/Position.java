/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

public interface Position
{
	Maybe<Integer> line();
	String lineContent();
	Maybe<Integer> offset();

	// pointer to the column position of the error/warning
	Maybe<Integer> pointer();
	Maybe<String> pointerSpace();

	Maybe<String> sourcePath();
	Maybe<java.io.File> sourceFile();	
}