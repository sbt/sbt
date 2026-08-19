/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import sbt.io.IO

/**
 * cleanFull used to delete the whole boot directory, including the jars the running server
 * itself is loaded from, so the server could no longer compile .sbt files
 * (MissingCoreLibraryException) until it was restarted by hand. It now keeps the in-use
 * boot artifacts.
 */
class CleanFullTest extends AbstractServerTest {
  override val testDirectory: String = "cleanfull"

  test("cleanFull keeps the server able to recompile the build definition") {
    assert(runBatchClient("compile") == 0, "initial compile must succeed")
    assert(runBatchClient("cleanFull") == 0, "cleanFull must complete with exit 0")
    IO.write(testPath.resolve("project/extra.sbt").toFile, "// force a build source change\n")
    assert(
      runBatchClient("compile") == 0,
      "compile after cleanFull and a build source change must succeed"
    )
  }
}
