/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import java.nio.file.{ Files, Path }
import _root_.sbt.internal.inc.MappedFileConverter
import _root_.sbt.internal.util.Attributed
import _root_.sbt.io.IO
import xsbti.{ FileConverter, HashedVirtualFileRef }

object GlobalPluginTest extends Properties:
  override def tests: List[Test] = List(
    example("reencode: a ref under a root that moved", movedRoot),
    example("reencode: a ref under a root that did not move", sharedRoot),
  )

  def movedRoot: Result =
    withRoots: (base, from, to) =>
      val jar = Files.createFile(from.resolve("a.jar"))
      val got = reencode(jar, converter(base, from), converter(base, to))
      converter(base, to).toPath(got) ==== jar

  def sharedRoot: Result =
    withRoots: (base, from, to) =>
      val jar = Files.createFile(base.resolve("a.jar"))
      val got = reencode(jar, converter(base, from), converter(base, to))
      got ==== converter(base, from).toVirtualFile(jar)

  def reencode(jar: Path, from: FileConverter, to: FileConverter): HashedVirtualFileRef =
    val cp = Seq(Attributed.blank(from.toVirtualFile(jar): HashedVirtualFileRef))
    GlobalPlugin.reencode(cp, from, to).head.data

  def converter(base: Path, out: Path): FileConverter =
    MappedFileConverter(Map("OUT" -> out, "BASE" -> base), true)

  def withRoots(f: (Path, Path, Path) => Result): Result =
    IO.withTemporaryDirectory: dir =>
      val root = dir.toPath
      val base = Files.createDirectory(root.resolve("base"))
      f(
        base,
        Files.createDirectory(root.resolve("from")),
        Files.createDirectory(root.resolve("to"))
      )
end GlobalPluginTest
