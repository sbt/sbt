/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt;

import java.io.Serializable;

public final class ForkConfiguration implements Serializable {
  private final boolean ansiCodesSupported;
  private final boolean parallel;

  public ForkConfiguration(final boolean ansiCodesSupported, final boolean parallel) {
    this.ansiCodesSupported = ansiCodesSupported;
    this.parallel = parallel;
  }

  public boolean isAnsiCodesSupported() {
    return ansiCodesSupported;
  }

  public boolean isParallel() {
    return parallel;
  }
}
