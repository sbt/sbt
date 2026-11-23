/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.ui

import java.nio.file.Files
import java.nio.file.attribute.{ PosixFileAttributeView, PosixFilePermissions }

import hedgehog.Result
import hedgehog.runner.*
import sbt.io.IO

object HistoryPermissionsTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "restricts existing history permissions without changing its contents",
      restrictsHistory
    ),
    example("leaves missing history files absent", leavesMissingHistoryAbsent),
  )

  private def restrictsHistory: Result =
    IO.withTemporaryDirectory: dir =>
      if Files.getFileStore(dir.toPath).supportsFileAttributeView(classOf[PosixFileAttributeView])
      then
        val contents = "compile\n"
        val history = Files.writeString(dir.toPath.resolve(".history"), contents)
        Files.setPosixFilePermissions(history, PosixFilePermissions.fromString("rw-r--r--"))
        UITask.restrictHistoryPermissions(history)
        assert(PosixFilePermissions.toString(Files.getPosixFilePermissions(history)) == "rw-------")
        assert(Files.readString(history) == contents)
      Result.success

  private def leavesMissingHistoryAbsent: Result =
    IO.withTemporaryDirectory: dir =>
      val history = dir.toPath.resolve(".history")
      UITask.restrictHistoryPermissions(history)
      assert(Files.notExists(history))
      Result.success
end HistoryPermissionsTest
