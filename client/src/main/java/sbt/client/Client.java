/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.client;

import sbt.internal.client.NetworkClient;

public class Client {
  public static void main(final String[] args) {
    boolean hadError = false;
    try {
      NetworkClient.main(args);
    } catch (final Throwable t) {
      t.printStackTrace();
      hadError = true;
    } finally {
      if (hadError) System.exit(1);
    }
  }
}
