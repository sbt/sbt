/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import org.scalasbt.shadedgson.com.google.gson.JsonParser
import sbt.internal.inc.{ Analysis, CompileOutput, MappedFileConverter }
import sbt.internal.worker.{
  CompileConfig,
  FileConverterConfig,
  HVFRURI,
  ScalaInstanceConfig,
  StringURI,
}
import sbt.io.IO
import sbt.util.InterfaceUtil.t2
import java.io.{ ByteArrayOutputStream, PrintStream }
import java.nio.file.{ Files, Path }
import scala.util.Try
import xsbti.{ HashedVirtualFileRef, VirtualFile }
import xsbti.compile.{
  AnalysisContents,
  CompileOrder,
  FileAnalysisStore,
  FileHash,
  MiniOptions,
  MiniSetup,
  PerClasspathEntryLookup,
}

object CompileMainSpec extends verify.BasicTestSuite:

  test("the classpath lookup serves the analysis of an upstream entry") {
    withProject: p =>
      val entries = p.upstreamEntries(1)
      val lookup = p.lookupFor(entries)
      assert(entries.forall((entry, _) => lookup.analysis(entry).isPresent))
  }

  /**
   * A `Map` of four entries or fewer compares keys with `equals`, which a `MappedVirtualFile`
   * answers on the id alone. Past that size the map hashes instead, and the hash of a
   * `HashedVirtualFileRef` folds in a content hash and a size that the classpath entry has no way
   * to reproduce, so no upstream project is ever found.
   */
  test("the classpath lookup serves upstream analysis past the small-map size") {
    withProject: p =>
      val entries = p.upstreamEntries(8)
      val lookup = p.lookupFor(entries)
      assert(entries.forall((entry, _) => lookup.analysis(entry).isPresent))
  }

  test("the classpath lookup finds nothing for an entry the map does not carry") {
    withProject: p =>
      val entries = p.upstreamEntries(8)
      val unknown = p.converter.toVirtualFile(p.upstreamJar("unknown.jar"))
      val lookup = p.lookupFor(entries)
      assert(!lookup.analysis(unknown).isPresent)
  }

  /**
   * Anything other than `CompileFailed` leaves `run` through `WorkerMain`, which turns it into an
   * error whose message may be null, and the server only learns about it from the exit poll.
   */
  test("a failure that is not CompileFailed is still reported as a json-rpc error") {
    withProject: p =>
      val out = ByteArrayOutputStream()
      val ran = Try(CompileMain.run(p.config(), 42L, PrintStream(out, true, "UTF-8")))
      assert(ran.isSuccess)
      val o = JsonParser.parseString(out.toString("UTF-8")).getAsJsonObject
      assert(o.getAsJsonPrimitive("id").getAsLong == 42L)
      assert(o.getAsJsonObject("error").getAsJsonPrimitive("message").getAsString.nonEmpty)
  }

  private class Project(root: Path):
    val out: Path = Files.createDirectories(root.resolve("out"))
    val converter: MappedFileConverter = MappedFileConverter(Map("OUT" -> out), true)

    def upstreamJar(name: String): Path =
      Files.write(out.resolve(name), Array.empty[Byte])

    /** One pickle jar and its analysis per upstream project, as `dependencyPicklePath` carries. */
    def upstreamEntries(n: Int): Vector[(VirtualFile, HVFRURI)] =
      (1 to n).toVector.map: i =>
        val entry = converter.toVirtualFile(upstreamJar(s"lib$i.jar"))
        val analysis = out.resolve(s"lib$i.zip")
        FileAnalysisStore
          .getDefault(analysis.toFile)
          .set(AnalysisContents.create(Analysis.empty, setup))
        entry -> HVFRURI(HashedVirtualFileRef.of(entry.id, "cafebabe", 1L), analysis.toUri)

    def lookupFor(entries: Vector[(VirtualFile, HVFRURI)]): PerClasspathEntryLookup =
      CompileMain.incSetup(config(analysisMap = entries.map(_._2))).perClasspathEntryLookup

    def config(analysisMap: Vector[HVFRURI] = Vector.empty): CompileConfig =
      CompileConfig(
        fileConverterConfig = FileConverterConfig(Vector(StringURI("OUT", out.toUri))),
        scalaInstanceConfig = ScalaInstanceConfig("0.0.0", Vector.empty, Vector.empty),
        bridgeJars = Vector.empty,
        sources = Vector.empty,
        externalDependencyJars = Vector.empty,
        output = out.resolve("classes").toUri,
        analysisFile = out.resolve("inc_compile.zip").toUri,
        earlyJarPath = None,
        scalacOptions = Vector.empty,
        javacOptions = Vector.empty,
        maxErrors = 100,
        analysisMap = analysisMap,
      )

    private def setup: MiniSetup =
      MiniSetup.of(
        CompileOutput(out.resolve("classes")),
        MiniOptions.of(Array.empty[FileHash], Array.empty[String], Array.empty[String]),
        "3.3.1",
        CompileOrder.Mixed,
        true,
        Array(t2("key" -> "value")),
      )
  end Project

  private def withProject(f: Project => Unit): Unit =
    IO.withTemporaryDirectory: tmp =>
      f(Project(tmp.toPath))
end CompileMainSpec
