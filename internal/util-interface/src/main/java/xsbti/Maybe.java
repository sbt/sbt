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
			public int hashCode() { return 17 + (v == null ? 0 : v.hashCode()); }
			public String toString() { return "Maybe.just(" + v + ")"; }
			public boolean equals(Object o) {
				if (o == null) return false;
				if (!(o instanceof Maybe)) return false;
				Maybe<?> other = (Maybe<?>) o;
				if (!other.isDefined()) return false;
				if (v == null) return other.get() == null;
				return v.equals(other.get());
			}
		};
	}
	public static <s> Maybe<s> nothing()
	{
		return new Maybe<s>() {
			public boolean isDefined() { return false; }
			public s get() { throw new UnsupportedOperationException("nothing.get"); }
			public int hashCode() { return 1; }
			public String toString() { return "Maybe.nothing()"; }
			public boolean equals(Object o) {
				if (o == null) return false;
				if (!(o instanceof Maybe)) return false;
				Maybe<?> other = (Maybe<?>) o;
				return !other.isDefined();
			}
		};

	}

	public final boolean isEmpty() { return !isDefined(); }
	public abstract boolean isDefined();
	public abstract t get();
}
