/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.Serializable;

public class TestLogInfo implements Serializable {
  public final long id;
  public final ForkTags tag;
  public final String message;

  public TestLogInfo(long id, ForkTags tag, String message) {
    this.id = id;
    this.tag = tag;
    this.message = message;
  }
}
