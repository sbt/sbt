/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.concurrent.atomic.AtomicBoolean
import java.io.InputStream

object ReadJson {
  def apply(in: InputStream, running: AtomicBoolean): String =
    new String(sbt.internal.util.ReadJsonFromInputStream(in, running, None).toArray, "UTF-8")
}
