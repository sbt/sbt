package sbt.internal

import hedgehog.*
import hedgehog.runner.*
import java.nio.file.Files

/**
 * Tests for [[Compiler.toConsoleScalacOptions]] — pipelining flags must be stripped before
 * reaching the REPL (#8921).
 */
object CompilerConsoleOptsTest extends Properties:
  override def tests: List[Test] = List(
    // ── deterministic unit cases ──────────────────────────────────────────────

    example("empty list stays empty", check(Seq.empty, Seq.empty)),
    example(
      "list with no pipelining flags is unchanged",
      check(Seq("-deprecation", "-feature"), Seq("-deprecation", "-feature"))
    ),
    example("-Ypickle-java alone is removed", check(Seq("-Ypickle-java"), Seq.empty)),
    example(
      "-Ypickle-java at the start is removed",
      check(Seq("-Ypickle-java", "-deprecation"), Seq("-deprecation"))
    ),
    example(
      "-Ypickle-java at the end is removed",
      check(Seq("-feature", "-Ypickle-java"), Seq("-feature"))
    ),
    example(
      "-Ypickle-java in the middle is removed",
      check(Seq("-deprecation", "-Ypickle-java", "-feature"), Seq("-deprecation", "-feature"))
    ),
    example(
      "-Ypickle-write with its argument are both removed",
      check(Seq("-Ypickle-write", "/tmp/early.jar"), Seq.empty)
    ),
    example(
      "-Ypickle-write at start removes flag and argument, keeps tail",
      check(Seq("-Ypickle-write", "/tmp/early.jar", "-deprecation"), Seq("-deprecation"))
    ),
    example(
      "-Ypickle-write at end removes flag and argument",
      check(Seq("-feature", "-Ypickle-write", "/tmp/early.jar"), Seq("-feature"))
    ),
    example(
      "-Ypickle-write in the middle removes flag and argument",
      check(
        Seq("-deprecation", "-Ypickle-write", "/tmp/early.jar", "-feature"),
        Seq("-deprecation", "-feature")
      )
    ),
    example(
      "-Ypickle-write without argument (trailing flag) is removed safely",
      check(Seq("-deprecation", "-Ypickle-write"), Seq("-deprecation"))
    ),
    example(
      "both pipelining flags together are removed",
      check(Seq("-Ypickle-java", "-Ypickle-write", "/tmp/early.jar"), Seq.empty)
    ),
    example(
      "both pipelining flags with surrounding options",
      check(
        Seq("-encoding", "utf8", "-Ypickle-java", "-Ypickle-write", "/tmp/early.jar", "-feature"),
        Seq("-encoding", "utf8", "-feature")
      )
    ),
    example(
      "multiple occurrences of -Ypickle-java are all removed",
      check(Seq("-Ypickle-java", "-opt:l:inline", "-Ypickle-java"), Seq("-opt:l:inline"))
    ),
    example(
      "multiple -Ypickle-write occurrences are all removed with their args",
      check(
        Seq("-Ypickle-write", "/a.jar", "-deprecation", "-Ypickle-write", "/b.jar"),
        Seq("-deprecation")
      )
    ),
    example(
      "real-world pipelining option list is cleaned",
      check(
        Seq(
          "-Ypickle-java",
          "-Ypickle-write",
          "/target/early/early.jar",
          "-encoding",
          "utf8",
          "-deprecation",
          "-feature"
        ),
        Seq("-encoding", "utf8", "-deprecation", "-feature")
      )
    ),
    example(
      "Vector input (as produced by sbt pipelining) is handled correctly",
      check(
        Vector(
          "-Ypickle-java",
          "-Ypickle-write",
          "/target/out/early/subproject_3-0.1.0-SNAPSHOT.jar",
          "-encoding",
          "utf8"
        ),
        Seq("-encoding", "utf8")
      )
    ),
    example(
      "virtualized compiler plugin paths are resolved for tool invocation",
      checkResolvedVirtualizedOptions
    ),

    // ── property-based cases ──────────────────────────────────────────────────

    property(
      "result never contains -Ypickle-java or -Ypickle-write",
      propNoPipeliningFlagsInResult
    ),
    property(
      "non-pipelining options are always preserved",
      propNonPipeliningOptionsPreserved
    ),
    property(
      "idempotent: applying twice gives the same result as applying once",
      propIdempotent
    ),
  )

  // ── helpers ───────────────────────────────────────────────────────────────

  private def check(input: Seq[String], expected: Seq[String]): Result =
    val got = Compiler.toConsoleScalacOptions(input)
    Result
      .assert(got == expected)
      .log(s"input:    $input")
      .log(s"expected: $expected")
      .log(s"got:      $got")

  private def checkResolvedVirtualizedOptions: Result =
    val cacheRoot = Files.createTempDirectory("compiler-console-opts")
    val rootPaths = Map("CSR_CACHE" -> cacheRoot)
    val converter = _root_.sbt.internal.inc.MappedFileConverter(rootPaths, allowMachinePath = false)
    val pluginJar = cacheRoot.resolve("plugins/acyclic.jar")
    val pluginRef = converter.toVirtualFile(pluginJar).toString
    val input = Seq(s"-Xplugin:$pluginRef", "-P:acyclic:force")
    val expected = Seq(s"-Xplugin:${pluginJar.toString}", "-P:acyclic:force")
    val got = Compiler.resolveVirtualizedScalacOptions(input, rootPaths)
    Result
      .assert(got == expected)
      .log(s"input:    $input")
      .log(s"expected: $expected")
      .log(s"got:      $got")

  private val pipeliningFlags = List("-Ypickle-java", "-Ypickle-write")

  /** Generate an arbitrary scalac-option token (flag or path-like argument). */
  private def genOption: Gen[String] =
    Gen.frequency1(
      7 -> Gen.element1(
        "-deprecation",
        "-feature",
        "-encoding",
        "utf8",
        "-opt:l:inline",
        "-Xfatal-warnings",
        "-unchecked"
      ),
      1 -> Gen.element1("-Ypickle-java"),
      1 -> Gen.element1("-Ypickle-write"),
      1 -> Gen.string(Gen.alphaNum, Range.linear(1, 30)).map("/" + _ + ".jar"),
    )

  private def genOptions: Gen[List[String]] =
    Gen.list(genOption, Range.linear(0, 20))

  def propNoPipeliningFlagsInResult: Property =
    for options <- genOptions.forAll
    yield
      val result = Compiler.toConsoleScalacOptions(options)
      Result
        .assert(!result.contains("-Ypickle-java"))
        .and(Result.assert(!result.contains("-Ypickle-write")))
        .log(s"input:  $options")
        .log(s"result: $result")

  def propNonPipeliningOptionsPreserved: Property =
    for
      options <- genOptions.forAll
      // build a list that contains only non-pipelining flags
      clean = options.filterNot(pipeliningFlags.contains)
    yield
      // inject the clean options as-is (no pipelining flags) and verify they survive
      val result = Compiler.toConsoleScalacOptions(clean)
      Result
        .assert(result == clean)
        .log(s"clean input: $clean")
        .log(s"result:      $result")

  def propIdempotent: Property =
    for options <- genOptions.forAll
    yield
      val once = Compiler.toConsoleScalacOptions(options)
      val twice = Compiler.toConsoleScalacOptions(once)
      Result
        .assert(once == twice)
        .log(s"input: $options")
        .log(s"once:  $once")
        .log(s"twice: $twice")

end CompilerConsoleOptsTest
