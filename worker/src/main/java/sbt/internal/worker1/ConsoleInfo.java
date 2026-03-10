/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;

public class ConsoleInfo implements Serializable {
  public ArrayList<URI> toolsJars;
  public ArrayList<URI> bridgeJars;
  public ArrayList<URI> products;
  public ArrayList<URI> classpathJars;
  public ArrayList<String> scalacOptions;
  public String initialCommands;
  public String cleanupCommands;

  public ConsoleInfo(
      ArrayList<URI> toolsJars,
      ArrayList<URI> bridgeJars,
      ArrayList<URI> products,
      ArrayList<URI> classpathJars,
      ArrayList<String> scalacOptions,
      String initialCommands,
      String cleanupCommands) {
    this.toolsJars = toolsJars;
    this.bridgeJars = bridgeJars;
    this.products = products;
    this.classpathJars = classpathJars;
    this.scalacOptions = scalacOptions;
    this.initialCommands = initialCommands;
    this.cleanupCommands = cleanupCommands;
  }
}
