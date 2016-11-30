package sbt
package internal
package librarymanagement

import sbt.io.IO
import sbt.librarymanagement.RawRepository

object FakeRawRepository {
  def create(name: String): RawRepository =
    new RawRepository(new FakeResolver(name, IO.createTemporaryDirectory, Map.empty))
}
