/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.ui

import java.nio.file.{ FileSystems, Files }
import java.nio.file.attribute.PosixFilePermissions

import scala.util.Using

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
      val options = java.util.Map.of(
        "create",
        "true",
        "enablePosixFileAttributes",
        "true",
        "defaultPermissions",
        "rw-r--r--",
      )
      Using.resource(FileSystems.newFileSystem(dir.toPath.resolve("history.zip"), options)): fs =>
        val contents = "compile\n"
        val history = Files.writeString(fs.getPath("/.history"), contents)
        assert(PosixFilePermissions.toString(Files.getPosixFilePermissions(history)) == "rw-r--r--")
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
