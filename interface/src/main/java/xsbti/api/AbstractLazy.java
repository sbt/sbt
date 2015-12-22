/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package xsbti.api;

import java.io.ObjectStreamException;

public abstract class AbstractLazy<T> implements Lazy<T>, java.io.Serializable
{
	// `writeReplace` must be `protected`, so that the `Impl` subclass
	// inherits the serialization override.
	//
	// (See source-dependencies/java-analysis-serialization-error, which would
	//  crash trying to serialize an AbstractLazy, because it pulled in an
	//  unserializable type eventually.)
	protected Object writeReplace() throws ObjectStreamException
	{
		return new StrictLazy<T>(get());
	}
	private static final class StrictLazy<T> implements Lazy<T>, java.io.Serializable
	{
		private final T value;
		StrictLazy(T t)
		{
			value = t;
		}
		public T get()
		{
			return value;
		}
	}
}