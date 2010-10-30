/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package xsbti.api;

	import java.io.ObjectStreamException;

public abstract class AbstractLazy<T> implements Lazy<T>, java.io.Serializable
{
	private Object writeReplace() throws ObjectStreamException
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