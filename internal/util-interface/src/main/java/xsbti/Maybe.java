/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbti;

/** Intended as a lightweight carrier for scala.Option. */
public abstract class Maybe<t> {
  private Maybe() {}

  @SuppressWarnings("unchecked")
  public static <s> Maybe<s> nothing() { return (Maybe<s>) Nothing.INSTANCE; }
  public static <s> Maybe<s> just(final s v) { return new Just<s>(v); }

  public static final class Just<s> extends Maybe<s> {
    private final s v;

    public Just(final s v) { this.v = v; }

    public s value() { return v; }

    public boolean isDefined() { return true; }
    public s get() { return v; }
    public int hashCode() { return 17 + (v == null ? 0 : v.hashCode()); }
    public String toString() { return "Maybe.just(" + v + ")"; }
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || !(o instanceof Just<?>)) return false;
      final Just<?> that = (Just<?>) o;
      return v == null ? that.v == null : v.equals(that.v);
    }
  }

  public static final class Nothing extends Maybe<Object> {
    public static final Nothing INSTANCE = new Nothing();
    private Nothing() { }

    public boolean isDefined() { return false; }
    public Object get() { throw new UnsupportedOperationException("nothing.get"); }

    public int hashCode() { return 1; }
    public String toString() { return "Maybe.nothing()"; }
    public boolean equals(Object o) { return this == o || o != null && o instanceof Nothing; }
  }

  public final boolean isEmpty() { return !isDefined(); }
  public abstract boolean isDefined();
  public abstract t get();
}
