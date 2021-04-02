/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util;

import org.jline.terminal.TerminalBuilder;

/**
 * This exists to a provide a wrapper to TerminalBuilder.setTerminalOverride that will not emit a
 * deprecation warning when called from scala.
 */
public class DeprecatedJLine {
  @SuppressWarnings("deprecation")
  public static void setTerminalOverride(final org.jline.terminal.Terminal terminal) {
    TerminalBuilder.setTerminalOverride(terminal);
  }
}
