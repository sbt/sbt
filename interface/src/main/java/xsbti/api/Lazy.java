/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package xsbti.api;

public interface Lazy<T>
{
	T get();
}