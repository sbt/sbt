/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.IOException;

public class ServerAlreadyBootingException extends Exception {

  public ServerAlreadyBootingException(IOException e) {
    super(e);
  }
}
