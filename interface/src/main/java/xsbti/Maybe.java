/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

/** Intended as a lightweight carrier for scala.Option. */
public abstract class Maybe<t>
{
	// private pending Scala bug #3642
	protected Maybe() {}

	public static <s> Maybe<s> just(final s v)
	{
		return new Maybe<s>() {
			public boolean isDefined() { return true; }
			public s get() { return v; }
		};
	}
	public static <s> Maybe<s> nothing()
	{
		return new Maybe<s>() {
			public boolean isDefined() { return false; }
			public s get() { throw new UnsupportedOperationException("nothing.get"); }
		};
	}

	public final boolean isEmpty() { return !isDefined(); }
	public abstract boolean isDefined();
	public abstract t get();
}