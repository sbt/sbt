/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.client;

import sbt.internal.client.NetworkClient;
import java.nio.file.Paths;
import org.fusesource.jansi.AnsiConsole;

public class Client {
  public static void main(final String[] args) {
    boolean isWin = System.getProperty("os.name").toLowerCase().startsWith("win");
    try {
      if (isWin) AnsiConsole.systemInstall();
      NetworkClient.main(args);
    } catch (final Throwable t) {
      t.printStackTrace();
    } finally {
      if (isWin) AnsiConsole.systemUninstall();
    }
  }
}
