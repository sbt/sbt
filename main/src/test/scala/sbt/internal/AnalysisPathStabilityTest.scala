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
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.nio.file.{ Files, Path }
import java.util.zip.GZIPInputStream
import _root_.sbt.internal.inc.{
  Analysis,
  CompileOutput,
  FarmHash,
  MappedFileConverter,
  SourceInfos
}
import _root_.sbt.internal.inc.Analysis.NonLocalProduct
import _root_.sbt.io.IO
import _root_.sbt.util.InterfaceUtil.t2
import scala.jdk.OptionConverters.*
import scala.util.Using
import xsbti.{ FileConverter, VirtualFileRef }
import xsbti.compile.{ AnalysisContents, CompileOrder, FileHash, MiniOptions, MiniSetup }

object AnalysisPathStabilityTest extends Properties:
  override def tests: List[Test] = List(
    example("a stored analysis holds no machine path", noMachinePathIsStored),
    example("a read analysis holds machine paths again", classpathEntriesAreRestored),
    example("two machines store the same bytes", rootsDoNotChangeTheBytes),
  )

  /**
   * The classpath hash is the one part of `MiniSetup` that holds `java.nio.file.Path` rather than
   * `VirtualFileRef`, so without a write mapper it carries the producing machine's paths.
   */
  def noMachinePathIsStored: Result =
    IO.withTemporaryDirectory: tmp =>
      val root = tmp.toPath
      val file = root.resolve("inc_compile.zip")
      storeOf(file, root).set(contentsOf(root))
      val stored = decompressed(file)
      Result.all(
        List(
          Result
            .assert(!stored.contains(s"$root"))
            .log(s"the stored analysis still holds the machine path $root"),
          Result
            .assert(stored.contains("$" + "{CSR_CACHE}/x/x.jar"))
            .log("the stored analysis holds no root-relative classpath entry"),
        )
      )

  /**
   * `LookupImpl.changedClasspathHash` compares the stored hash against the concrete one of the
   * current compile, so reading has to undo what writing mapped.
   */
  def classpathEntriesAreRestored: Result =
    IO.withTemporaryDirectory: tmp =>
      val root = tmp.toPath
      val file = root.resolve("inc_compile.zip")
      storeOf(file, root).set(contentsOf(root))
      val got = storeOf(file, root).get().toScala
      val entries = got.toList.flatMap(_.getMiniSetup.options.classpathHash.map(_.file))
      Result
        .assert(entries == List(jarIn(root)))
        .log(s"expected ${jarIn(root)} to be read back, got $entries")

  /** The point of the mappers: the same compile on two machines has to serialize to the same bytes. */
  def rootsDoNotChangeTheBytes: Result =
    IO.withTemporaryDirectory: tmp =>
      val one = Files.createDirectories(tmp.toPath.resolve("machine-one"))
      val two = Files.createDirectories(tmp.toPath.resolve("machine-two-with-longer-name"))
      val fileOne = one.resolve("inc_compile.zip")
      val fileTwo = two.resolve("inc_compile.zip")
      storeOf(fileOne, one).set(contentsOf(one))
      storeOf(fileTwo, two).set(contentsOf(two))
      Result
        .assert(Files.readAllBytes(fileOne).sameElements(Files.readAllBytes(fileTwo)))
        .log("the same analysis serialized to different bytes under different root paths")

  // ---------- helpers ----------

  private def jarIn(root: Path): Path = root.resolve("csr").resolve("x").resolve("x.jar")

  private def converterFor(root: Path): FileConverter =
    MappedFileConverter(Map("CSR_CACHE" -> root.resolve("csr")), allowMachinePath = true)

  private def storeOf(file: Path, root: Path) =
    BuildDef.cachedAnalysisStore(file, converterFor(root))

  private def contentsOf(root: Path): AnalysisContents =
    AnalysisContents.create(oneSourceAnalysis, setupFor(root))

  private def setupFor(root: Path): MiniSetup =
    MiniSetup.of(
      CompileOutput(root.resolve("classes")),
      MiniOptions.of(Array(FileHash.of(jarIn(root), 1)), Array("-deprecation"), Array.empty),
      "3.3.1",
      CompileOrder.Mixed,
      true,
      Array(t2("key" -> "value")),
    )

  private def oneSourceAnalysis: Analysis =
    val stamp = FarmHash.fromLong(1L)
    Analysis.empty.addSource(
      src = VirtualFileRef.of("A.scala"),
      apis = Nil,
      stamp = stamp,
      info = SourceInfos.emptyInfo,
      nonLocalProducts = NonLocalProduct("A", "A", VirtualFileRef.of("A.class"), stamp) :: Nil,
      localProducts = Nil,
      internalDeps = Nil,
      externalDeps = Nil,
      libraryDeps = (VirtualFileRef.of("x.jar"), "x", stamp) :: Nil,
    )

  private def decompressed(file: Path): String =
    Using.resource(new GZIPInputStream(Files.newInputStream(file))): in =>
      new String(in.readAllBytes(), ISO_8859_1)

end AnalysisPathStabilityTest
