/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.nio.file.{ Files, FileSystems }
import java.nio.file.attribute.PosixFilePermissions
import hedgehog.*
import hedgehog.runner.*

object RuntimeDirectoryTest extends Properties:
  override def tests: List[Test] = List(
    example("a new runtime directory is private", testNewDirectory),
    example("a directory others can write to is rejected", testSharedDirectory),
  )

  private def posix = FileSystems.getDefault.supportedFileAttributeViews.contains("posix")

  def testNewDirectory: Result =
    if !posix then Result.success
    else
      _root_.sbt.io.IO.withTemporaryDirectory { tmp =>
        val dir = tmp.toPath.resolve("mine")
        Result.all(
          List(
            Result.assert(SysProp.isPrivateDirectory(dir)),
            PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)) ==== "rwx------"
          )
        )
      }

  def testSharedDirectory: Result =
    if !posix then Result.success
    else
      _root_.sbt.io.IO.withTemporaryDirectory { tmp =>
        val dir = Files.createDirectory(tmp.toPath.resolve("shared"))
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxrwxrwx"))
        Result.assert(!SysProp.isPrivateDirectory(dir))
      }
end RuntimeDirectoryTest
