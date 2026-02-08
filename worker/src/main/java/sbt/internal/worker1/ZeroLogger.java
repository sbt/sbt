/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.util.function.Supplier;
import xsbti.Logger;

public class ZeroLogger implements Logger {
  public void error(Supplier<String> msg) {}

  public void warn(Supplier<String> msg) {}

  public void info(Supplier<String> msg) {}

  public void debug(Supplier<String> msg) {}

  public void trace(Supplier<Throwable> exception) {}
}
