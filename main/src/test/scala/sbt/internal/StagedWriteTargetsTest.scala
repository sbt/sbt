package sbt.internal

import hedgehog.*
import hedgehog.runner.*

object StagedWriteTargetsTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "staging file of a resource maps to the resource",
      check("sbt/sbt.autoplugins.def37158.tmp", Seq("sbt/sbt.autoplugins"))
    ),
    example(
      "staging file of a short name may carry the sbt- prefix",
      check("a/sbt-ab.0123abcd.tmp", Seq("a/sbt-ab", "a/ab"))
    ),
    example("windows separators are kept", check("a\\sbt-b.0123abcd.tmp", Seq("a\\sbt-b", "a\\b"))),
    example("long sbt- names are not unprefixed", check("sbt-abc.0123abcd.tmp", Seq("sbt-abc"))),
    example("other tmp files are not staging files", check("a/b.tmp", Nil)),
    example("class files are not staging files", check("a/B.class", Nil)),
  )

  private def check(path: String, expected: Seq[String]): Result =
    Result.assert(Compiler.stagedWriteTargets(path) == expected)
