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
import java.nio.file.attribute.FileTime
import _root_.sbt.internal.inc.{
  Analysis,
  Compilation,
  Compilations,
  CompileOutput,
  FarmHash,
  FileAnalysisStore,
  MappedFileConverter,
  SourceInfos
}
import _root_.sbt.internal.inc.Analysis.NonLocalProduct
import _root_.sbt.io.IO
import _root_.sbt.io.syntax.*
import _root_.sbt.util.InterfaceUtil.t2
import scala.jdk.OptionConverters.*
import xsbti.{ FileConverter, VirtualFileRef }
import xsbti.compile.{
  AnalysisContents,
  AnalysisStore,
  CompileOrder,
  FileHash,
  MiniOptions,
  MiniSetup
}

object LocalAnalysisCacheTest extends Properties:
  override def tests: List[Test] = List(
    example("a stored analysis is served from the local cache", storedAnalysisIsServed),
    example("a re-created analysis file stays cached", reCreatedFileStaysCached),
    example("a rewritten analysis file is read again", rewrittenFileIsReadAgain),
    example("the compilations a read drops are not served", compilationsAreNotServed),
  )

  /**
   * `compileIncremental` stores the analysis it just computed, and the next reader goes through a
   * store of its own, so the value has to come from the process-wide cache rather than from a field
   * of the instance that wrote it.
   */
  def storedAnalysisIsServed: Result =
    withAnalysisFile: file =>
      val contents = contentsOf(oneSourceAnalysis)
      cachedStore(file).set(contents)
      val got = cachedStore(file).get().toScala
      Result
        .assert(got.exists(_ eq contents))
        .log("expected the stored analysis, not a re-read of the file")

  /**
   * The analysis file is a declared output of `compileIncremental`, so the action cache re-creates
   * it in place right after it is written: same content under a new timestamp.
   */
  def reCreatedFileStaysCached: Result =
    withAnalysisFile: file =>
      val contents = contentsOf(oneSourceAnalysis)
      val store = cachedStore(file)
      store.set(contents)
      reCreate(file)
      val got = store.get().toScala
      Result
        .assert(got.exists(_ eq contents))
        .log("re-creating the file with the same content must not discard the cached analysis")

  /** A file the action cache switches to another analysis holds that other analysis. */
  def rewrittenFileIsReadAgain: Result =
    withAnalysisFile: file =>
      val contents = contentsOf(oneSourceAnalysis)
      val store = cachedStore(file)
      store.set(contents)
      FileAnalysisStore.binary(file.toFile).set(contentsOf(Analysis.empty))
      val got = store.get().toScala
      Result.all(
        List(
          Result
            .assert(!got.exists(_ eq contents))
            .log("must not serve the analysis the file no longer holds"),
          Result
            .assert(got.exists(_.getAnalysis.readStamps.getAllSourceStamps.isEmpty))
            .log("expected the analysis now on disk"),
        )
      )

  /**
   * The binary format does not persist compilations, and `compileScalaBackend` reads them back to
   * decide whether the compile modified anything, so a stored analysis may not carry its own.
   */
  def compilationsAreNotServed: Result =
    withAnalysisFile: file =>
      val compiled = oneSourceAnalysis.copy(
        compilations = Compilations.empty.add(Compilation(1L, setup.output))
      )
      val store = cachedStore(file)
      store.set(contentsOf(compiled))
      val fromCache = store.get().toScala
      inMemoryStore(file).set(contentsOf(compiled))
      val fromFile = inMemoryStore(file).get().toScala
      Result.all(
        List(
          Result
            .assert(fromCache.exists(_.getAnalysis.readCompilations.getAllCompilations.isEmpty))
            .log("a stored analysis must be served as the file holds it"),
          Result
            .assert(fromFile.exists(_.getAnalysis.readCompilations.getAllCompilations.isEmpty))
            .log("expected the file itself to hold no compilations"),
        )
      )

  // ---------- helpers ----------

  private val converter: FileConverter = MappedFileConverter.empty

  private def cachedStore(file: Path): AnalysisStore =
    BuildDef.cachedAnalysisStore(file, converter)

  private def inMemoryStore(file: Path): AnalysisStore =
    FileAnalysisStore.binary(file.toFile)

  /** Replaces `file` with its own content, as syncing an action cache output does. */
  private def reCreate(file: Path): Unit =
    val content = Files.readAllBytes(file)
    val lastModified = Files.getLastModifiedTime(file).toMillis
    IO.delete(file.toFile)
    Files.write(file, content)
    Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified + 5000))

  private def withAnalysisFile(f: Path => Result): Result =
    IO.withTemporaryDirectory: tmp =>
      f((tmp / "inc_compile.zip").toPath)

  private val setup: MiniSetup =
    MiniSetup.of(
      CompileOutput((file("target") / "classes").toPath),
      MiniOptions.of(Array.empty[FileHash], Array.empty[String], Array.empty[String]),
      "3.3.1",
      CompileOrder.Mixed,
      true,
      Array(t2("key" -> "value")),
    )

  private def contentsOf(analysis: Analysis): AnalysisContents =
    AnalysisContents.create(analysis, setup)

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

end LocalAnalysisCacheTest
