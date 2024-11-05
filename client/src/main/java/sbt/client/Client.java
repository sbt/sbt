/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.client;

import sbt.internal.client.NetworkClient;
import org.jline.jansi.AnsiConsole;

public class Client {
  public static void main(final String[] args) {
    boolean isWin = System.getProperty("os.name").toLowerCase().startsWith("win");
    boolean hadError = false;
    try {
      if (isWin) AnsiConsole.systemInstall();
      NetworkClient.main(args);
    } catch (final Throwable t) {
      t.printStackTrace();
      hadError = true;
    } finally {
      if (isWin) AnsiConsole.systemUninstall();
      if (hadError) System.exit(1);
    }
  }
}
