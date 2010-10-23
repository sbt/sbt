/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
package xsbti.api;

public interface Lazy<T>
{
	T get();
}