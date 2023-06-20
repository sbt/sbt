/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package librarymanagement

import sbt.io.IO
import sbt.librarymanagement.RawRepository

object FakeRawRepository {
  def create(name: String): RawRepository =
    new RawRepository(new FakeResolver(name, IO.createTemporaryDirectory, Map.empty), name)
}
