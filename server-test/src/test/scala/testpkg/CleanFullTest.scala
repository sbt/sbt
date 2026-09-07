/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.io.File

import sbt.io.IO

/**
 * cleanFull used to delete the whole boot directory, including the jars the running server
 * itself is loaded from, so the server could no longer compile .sbt files
 * (MissingCoreLibraryException) until it was restarted by hand. It now keeps the in-use
 * boot artifacts until the server shuts down, so commands chained after cleanFull still work,
 * and deletes them on exit so the next invocation starts from a fresh JVM, boot directory and
 * metabuild.
 */
class CleanFullTest extends AbstractServerTest {
  override val testDirectory: String = "cleanfull"

  test("cleanFull keeps chained commands working and then shuts the server down") {
    assert(runBatchClient("compile") == 0, "initial compile must succeed")
    assert(
      runBatchClient("cleanFull; compile") == 0,
      "compile chained after cleanFull must succeed"
    )
    val deadline = System.currentTimeMillis + 60000
    while (svr.isAlive && System.currentTimeMillis < deadline) Thread.sleep(500)
    assert(!svr.isAlive, "server must shut itself down after cleanFull")
    val bootDirectory = new File(sys.props("user.home"), ".sbt/scripted/boot")
    val bootContents = Option(bootDirectory.list).toList.flatten
    assert(
      bootContents.isEmpty,
      s"boot directory must be empty after server shutdown: ${bootContents.mkString(", ")}"
    )

    restartServer()
    IO.write(testPath.resolve("project/extra.sbt").toFile, "// force a build source change\n")
    assert(
      runBatchClient("compile") == 0,
      "compile on a fresh server after cleanFull and a build change must succeed"
    )
  }
}
