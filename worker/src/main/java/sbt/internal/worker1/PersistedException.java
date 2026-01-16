/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

public class PersistedException extends Throwable {
  private String className;

  public PersistedException(String message, Throwable cause, String className) {
    super(message, cause);
    this.className = className;
  }
}
