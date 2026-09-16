/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interfaces.Diagnostic.WARNING
import dotty.tools.dotc.reporting.Diagnostic
import hedgehog.Result
import hedgehog.runner.*
import java.nio.file.{ Path, Paths }
import sbt.internal.{ Eval, EvalImports, EvalReporter }
import scala.io.Source
import scala.util.Using

object UncachedOutputWarningSpec extends Properties:
  private val warning = "Def.declareOutput has no caching effect in an uncached task."
  private val imports = EvalImports(Seq("import sbt.*", "import sbt.given", "import sbt.Keys.*"))
  private lazy val classpath: Seq[Path] =
    val resource = Option(getClass.getResourceAsStream("/sbt-eval-classpath.txt"))
      .getOrElse(sys.error("Missing generated sbt evaluator classpath"))
    Using.resource(Source.fromInputStream(resource, "UTF-8")):
      _.getLines().map(Paths.get(_)).toVector

  private val body = """{
    |  val output = sbt.internal.util.StringVirtualFile1("output.txt", "content")
    |  Def.declareOutput(output)
    |  ()
    |}""".stripMargin

  override def tests: List[Test] = List(
    ("uncached task", s"Def.uncachedTask $body", Vector(WARNING -> warning)),
    (
      "transient task",
      s"object Build { @transient lazy val task = taskKey[Unit](\"\"); val setting = task := $body }; Build.setting",
      Vector(WARNING -> warning)
    ),
    (
      "opted-out task",
      s"val task = taskKey[Unit](\"\"); task := Def.uncached $body",
      Vector(WARNING -> warning)
    ),
    ("cached task", s"Def.cachedTask $body", Vector.empty),
    ("uncached task without an output declaration", "Def.uncachedTask(())", Vector.empty),
  ).map: (name, code, expected) =>
    example(
      name, {
        val reporter = new RecordingReporter
        val evaluator =
          new Eval(Nil, classpath, backingDir = None, mkReporter = Some(() => reporter))
        evaluator.eval(code, imports, None, "uncached-output-build.sbt", 1)
        Result.assert(reporter.diagnostics == expected).log(reporter.diagnostics.mkString("\n"))
      }
    )

  private final class RecordingReporter extends EvalReporter:
    var diagnostics = Vector.empty[(Int, String)]

    override def doReport(diagnostic: Diagnostic)(using Context): Unit =
      diagnostics :+= diagnostic.level -> diagnostic.msg.message

    override def finalReport(sourceName: String): Unit = ()
end UncachedOutputWarningSpec
