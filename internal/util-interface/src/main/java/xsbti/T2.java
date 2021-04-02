/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

/** Used to pass a pair of values. */
public interface T2<A1, A2> {
  public A1 get1();

  public A2 get2();
}
