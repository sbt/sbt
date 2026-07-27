/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.{ File, PrintWriter, StringWriter }
import sbt.io.IO
import sbt.librarymanagement.*
import sbt.util.CacheStore
import sjsonnew.support.scalajson.unsafe.{ CompactPrinter, Converter, Parser }

object UpdateReportCacheCodecSpec extends verify.BasicTestSuite:

  test("writing a report does not depend on the content of the files it references"):
    // The default File codec hashes file contents with SHA-256. If it is still in play, changing a
    // jar's bytes changes the serialized report; with the hash disabled the two are identical.
    IO.withTemporaryDirectory: dir =>
      val jar = new File(dir, "lib.jar")
      IO.write(jar, "first contents")
      val before = render(report(dir, jar))
      IO.write(jar, "totally different contents, and a different length")
      val after = render(report(dir, jar))
      assert(
        before == after,
        s"the serialized report must not encode file contents:\n$before\n$after"
      )

  test("a File round trips to the same path"):
    IO.withTemporaryDirectory: dir =>
      val jar = new File(dir, "lib.jar")
      IO.write(jar, "x")
      val parsed = Parser.parseFromString(render(report(dir, jar))).get
      val ur =
        Converter
          .fromJson[UpdateReport](parsed)(using
            UpdateReportPersistence.CacheCodec.UpdateReportFormat
          )
          .get
      assert(ur.configurations.head.modules.head.artifacts.head._2 == jar)

  test("a cache written with a real content hash still reads"):
    // Caches in the wild hold {"first": <uri>, "second": <sha256>}. Reading must ignore the Long,
    // not require it to be zero.
    IO.withTemporaryDirectory: dir =>
      val jar = new File(dir, "lib.jar")
      IO.write(jar, "x")
      val js =
        Converter.toJson(report(dir, jar))(using LibraryManagementCodec.UpdateReportFormat).get
      val out = new StringWriter
      CompactPrinter.print(js, new PrintWriter(out))
      val text = out.toString
      assert(!text.contains("\"second\":0,"), "fixture should carry a real hash, not zero")
      val ur = Converter
        .fromJson[UpdateReport](Parser.parseFromString(text).get)(using
          UpdateReportPersistence.CacheCodec.UpdateReportFormat
        )
        .get
      assert(ur.configurations.head.modules.head.artifacts.head._2 == jar)

  test("the cache UpdateReportPersistence writes carries no content hash"):
    // The codec only matters if `UpdateReportPersistence` resolves through it. Its own `given` import is
    // what reroutes the artifact pairs nested inside the generated formats, so this pins the wiring
    // rather than the codec -- writing through the stock codec instead would still compile.
    IO.withTemporaryDirectory: dir =>
      val jar = new File(dir, "lib.jar")
      IO.write(jar, "contents that would hash to something other than zero")
      val out = new File(dir, "out.json")
      UpdateReportPersistence
        .writeTo(CacheStore(out), UpdateReportPersistence.toCache(report(dir, jar)))
      val text = IO.read(out)
      val hashes = """"second":(-?\d+)""".r.findAllMatchIn(text).map(_.group(1)).toVector
      assert(hashes.nonEmpty, s"expected the cache to name files at all:\n$text")
      assert(
        hashes.forall(_ == "0"),
        s"every File pair must carry a zero hash, got $hashes:\n$text"
      )

  private def render(ur: UpdateReport): String =
    val js = Converter.toJson(ur)(using UpdateReportPersistence.CacheCodec.UpdateReportFormat).get
    val out = new StringWriter
    CompactPrinter.print(js, new PrintWriter(out))
    out.toString

  private def report(dir: File, jar: File): UpdateReport =
    val modId = ModuleID("org.example", "lib", "1.0.0")
    val artifact = Artifact("lib", "jar", "jar", None, Vector.empty, None, Map.empty, None)
    val mr = ModuleReport(modId, Vector((artifact, jar)), Vector.empty)
    val descriptor = new File(dir, "ivy.xml")
    IO.touch(descriptor)
    UpdateReport(
      descriptor,
      Vector(ConfigurationReport(ConfigRef("compile"), Vector(mr), Vector.empty)),
      UpdateStats(0L, 0L, 0L, false),
      Map.empty
    )
