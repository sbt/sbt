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
import java.io.File
import java.net.URI
import java.nio.file.{ Files, Path }
import scala.util.Try
import _root_.sbt.internal.inc.MappedFileConverter
import _root_.sbt.internal.util.Attributed
import _root_.sbt.io.IO
import _root_.sbt.librarymanagement.{ ModuleID, UpdateReport, UpdateStats }
import xsbti.{ FileConverter, HashedVirtualFileRef }

object GlobalPluginTest extends Properties:
  override def tests: List[Test] = List(
    example("reencode: a path-based ref under a root that moved", movedRoot),
    example("reencode: a plain ref under a root that moved", movedRootPlain),
    example("reencode: a directory under a root that moved", movedDirectory),
    example("reencode: a ref under a root that did not move", sharedRoot),
    example("reencode: a ref under an old root inside the base", outInBase),
    example("reencode: an analysis under a root that moved", movedAnalysis),
    example("reencode: a ref under a root that moved, without machine paths", noMachinePath),
    example("inject: an exported jar URI with an authority", uriWithAuthority),
  )

  def movedRoot: Result =
    withRoots: (base, from, to) =>
      val jar = Files.createFile(from.resolve("a.jar"))
      val ref = converter(base, from).toVirtualFile(jar)
      val got = reencode(ref, converter(base, from), converter(base, to))
      got.id ==== converter(base, to).toVirtualFile(jar).id

  def movedRootPlain: Result =
    withRoots: (base, from, to) =>
      val jar = Files.createFile(from.resolve("a.jar"))
      val ref = HashedVirtualFileRef.of("${OUT}/a.jar", "")
      val got = reencode(ref, converter(base, from), converter(base, to))
      got.id ==== converter(base, to).toVirtualFile(jar).id

  def movedDirectory: Result =
    withRoots: (base, from, to) =>
      val dir = Files.createDirectory(from.resolve("classes"))
      val ref = converter(base, from).toVirtualFile(dir)
      val got = reencode(ref, converter(base, from), converter(base, to))
      got.id ==== converter(base, to).toVirtualFile(dir).id

  def sharedRoot: Result =
    withRoots: (base, from, to) =>
      val jar = Files.createFile(base.resolve("a.jar"))
      val ref = converter(base, from).toVirtualFile(jar)
      val got = reencode(ref, converter(base, from), converter(base, to))
      got ==== ref

  def outInBase: Result =
    withRoots: (base, _, to) =>
      val from = Files.createDirectories(base.resolve("target/out"))
      val jar = Files.createFile(from.resolve("a.jar"))
      val ref = HashedVirtualFileRef.of("${OUT}/a.jar", "")
      val got = reencode(ref, converter(base, from), converter(base, to))
      got.id ==== "${BASE}/target/out/a.jar"

  def movedAnalysis: Result =
    withRoots: (base, from, to) =>
      val entry = Attributed
        .blank(HashedVirtualFileRef.of("${OUT}/a.jar", ""))
        .put(Keys.analysis, "${OUT}/a.zip")
      val got = GlobalPlugin.reencode(Seq(entry), converter(base, from), converter(base, to))
      got.head.get(Keys.analysis) ==== Some(
        converter(base, to).toVirtualFile(from.resolve("a.zip")).id
      )

  def noMachinePath: Result =
    withRoots: (base, from, to) =>
      val jar = Files.createFile(from.resolve("a.jar"))
      val ref = converter(base, from).toVirtualFile(jar)
      val dst = MappedFileConverter(Map("OUT" -> to, "BASE" -> base), false)
      Result.assert(Try(reencode(ref, converter(base, from), dst)).isFailure)

  def uriWithAuthority: Result =
    val report = UpdateReport(File("update"), Vector.empty, UpdateStats(0, 0, 0, false), Map.empty)
    val data = GlobalPluginData(
      ModuleID("org.example", "gp", "0.0"),
      Nil,
      Vector.empty,
      Nil,
      Nil,
      Vector(URI("file://server/share/a.jar"))
    )(report, MappedFileConverter.empty)
    Result.assert(Try(GlobalPlugin.inject(data)).isSuccess)

  def reencode(
      ref: HashedVirtualFileRef,
      from: FileConverter,
      to: FileConverter
  ): HashedVirtualFileRef =
    GlobalPlugin.reencode(Seq(Attributed.blank(ref)), from, to).head.data

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
