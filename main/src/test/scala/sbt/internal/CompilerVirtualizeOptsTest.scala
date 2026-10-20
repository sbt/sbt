package sbt.internal

import hedgehog.*
import hedgehog.runner.*
import java.nio.file.{ Path, Paths }

/**
 * Tests for [[Compiler.virtualizeScalacOptions]]: the compile cache key must not name the
 * checkout that sbt's output paths in scalacOptions point into.
 */
object CompilerVirtualizeOptsTest extends Properties:
  private val tmp = Paths.get(System.getProperty("java.io.tmpdir")).toAbsolutePath
  private val out = tmp.resolve("compiler-virtualize-opts").resolve("out")
  private val outside = tmp.resolve("compiler-virtualize-opts-outside")
  private val rootPaths: Map[String, Path] = Map("OUT" -> out)

  override def tests: List[Test] = List(
    example(
      "the -Ypickle-write jar under OUT is virtualized",
      check(
        Seq("-Ypickle-java", "-Ypickle-write", out.resolve("early").resolve("a_3.jar").toString),
        Seq("-Ypickle-java", "-Ypickle-write", "${OUT}/early/a_3.jar")
      )
    ),
    example(
      "the -semanticdb-target under OUT is virtualized",
      check(
        Seq("-semanticdb-target", out.resolve("meta").toString),
        Seq("-semanticdb-target", "${OUT}/meta")
      )
    ),
    example(
      "the Scala 2 semanticdb targetroot under OUT is virtualized",
      check(
        Seq(s"-P:semanticdb:targetroot:${out.resolve("meta")}"),
        Seq("-P:semanticdb:targetroot:${OUT}/meta")
      )
    ),
    example(
      "paths under OUT in other options are unchanged",
      check(
        Seq(s"-Xplugin:${out.resolve("p.jar")}", "-sourceroot", out.toString),
        Seq(s"-Xplugin:${out.resolve("p.jar")}", "-sourceroot", out.toString)
      )
    ),
    example(
      "output paths outside OUT are unchanged",
      check(
        Seq("-semanticdb-target", outside.resolve("meta").toString),
        Seq("-semanticdb-target", outside.resolve("meta").toString)
      )
    ),
    example(
      "a trailing output flag without its value is unchanged",
      check(Seq("-deprecation", "-Ypickle-write"), Seq("-deprecation", "-Ypickle-write"))
    ),
  )

  private def check(input: Seq[String], expected: Seq[String]): Result =
    val got = Compiler.virtualizeScalacOptions(input, rootPaths)
    Result
      .assert(got == expected)
      .log(s"input:    $input")
      .log(s"expected: $expected")
      .log(s"got:      $got")

end CompilerVirtualizeOptsTest
