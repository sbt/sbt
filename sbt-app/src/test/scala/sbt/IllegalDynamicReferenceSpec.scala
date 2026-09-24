/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.interfaces.Diagnostic.ERROR
import dotty.tools.dotc.reporting.Diagnostic
import hedgehog.Result
import hedgehog.runner.*
import java.nio.file.{ Path, Paths }
import sbt.internal.{ Eval, EvalException, EvalImports, EvalReporter }
import scala.io.Source
import scala.util.Using

object IllegalDynamicReferenceSpec extends Properties:
  private val sourceName = "i1095-build.sbt"
  private val imports = EvalImports(Seq("import sbt.*", "import sbt.given", "import sbt.Keys.*"))
  private lazy val classpath: Seq[Path] =
    val resource = Option(getClass.getResourceAsStream("/sbt-eval-classpath.txt"))
      .getOrElse(sys.error("Missing generated sbt evaluator classpath"))
    Using.resource(Source.fromInputStream(resource, "UTF-8")):
      _.getLines().map(Paths.get(_)).toVector

  private val filterExpression =
    "ScopeFilter(inProjects(ThisProject), inConfigurations(Compile))"

  private val validDefinitions = List(
    "outer val" -> s"val filter = $filterExpression; sources := sources.all(filter).value.flatten",
    "outer lazy val" -> s"lazy val filter = $filterExpression; sources := sources.all(filter).value.flatten",
    "enclosing block" -> s"{ val filter = $filterExpression; sources := sources.all(filter).value.flatten }",
    "helper parameter" -> s"def collect(filter: ScopeFilter) = Def.task { sources.all(filter).value.flatten }; sources := collect($filterExpression).value",
    "dynamic parent local" -> s"sources := Def.taskDyn { val filter = $filterExpression; Def.task { sources.all(filter).value.flatten } }.value",
    "dynamic parent pattern binder" -> s"Def.taskDyn { Option($filterExpression) match { case Some(filter) => Def.task { sources.all(filter).value.flatten }; case None => Def.task(Seq.empty[File]) } }",
    "contained pattern binder" -> s"sources := (Option($filterExpression) match { case Some(filter) => sources.all(filter); case None => sources.all($filterExpression) }).value.flatten",
    "result locals" -> "Def.task { val count = 2; val result = count + 1; result }",
    "dependency result local" -> "Def.task { val base = baseDirectory.value; base.getName.length }",
    "contained definition" -> s"sources := ({ val filter = $filterExpression; sources.all(filter) }).value.flatten",
    "shadowed name" -> s"val filter = $filterExpression; sources := { val collected = sources.all(filter).value; val count = { val filter = 2; filter }; collected.flatten.take(count) }",
    "lambda parameter" -> s"sources := List($filterExpression).map(filter => Def.task { sources.all(filter).value.flatten }).head.value",
    "conditional task" -> "Def.task { if (baseDirectory.value.exists) Def.task(1).value else Def.task(2).value }",
    "higher-kinded call" -> "class Box[A]; def build[F[_]](value: String): String = value; Def.task { build[Box](\"value\") }",
    "higher-kinded call with dependency" -> "class Box[A]; object Builder { def apply[F[_]](value: String): String = value }; Def.cachedTask { Builder[Box](name.value) }",
    "sequential task in dynamic task" -> "Def.taskDyn[Int] { Def.unit(baseDirectory.value); Def.sequential(Def.task(42)) }",
  )

  private val invalidDefinitions = List(
    "task assignment" -> s"sources := { val filter = $filterExpression; sources.all(<filter>).value.flatten }",
    "explicit task" -> s"Def.task { val filter = $filterExpression; sources.all(<filter>).value.flatten }",
    "pattern-bound filter" -> s"""Def.task {
      |  Option($filterExpression) match {
      |    case Some(filter) => sources.all(<filter>).value.flatten
      |    case None => Seq.empty[File]
      |  }
      |}""".stripMargin,
    "two-project aggregation" -> """lazy val api = project
      |lazy val engine = project
      |sources := {
      |  val filter = ScopeFilter(inProjects(api, engine), inConfigurations(Compile))
      |  sources.all(<filter>).value.flatten
      |}""".stripMargin,
    "multiple inputs and renamed local" -> s"""Def.task {
      |  val selected = $filterExpression
      |  val base = baseDirectory.value
      |  sources.all(<selected>).value.flatten.size + base.getName.length
      |}""".stripMargin,
    "cached task" -> s"Def.cachedTask { val filter = $filterExpression; sources.all(<filter>).value.size }",
    "uncached task" -> s"Def.uncachedTask { val filter = $filterExpression; sources.all(<filter>).value.size }",
    "dynamic task dependency" -> s"Def.taskDyn { val filter = $filterExpression; val collected = sources.all(<filter>).value; Def.task(collected.size) }",
  )

  override def tests: List[Test] = validDefinitions.map: (name, code) =>
    example(
      s"$name remains valid", {
        val (compiled, diagnostics) = compile(code)
        Result
          .assert(compiled)
          .and(Result.assert(diagnostics.isEmpty))
          .log(diagnostics.mkString("\n"))
      }
    )
  ++ invalidDefinitions.map: (name, markedCode) =>
    example(
      s"$name receives an actionable diagnostic at its use", {
        val start = markedCode.indexOf('<')
        val end = markedCode.indexOf('>', start)
        val identifier = markedCode.substring(start + 1, end)
        val prefix = markedCode.take(start)
        val code = prefix + identifier + markedCode.drop(end + 1)
        val line = prefix.count(_ == '\n')
        val column = prefix.length - prefix.lastIndexOf('\n') - 1
        val (compiled, diagnostics) = compile(code)
        val expected =
          s"Illegal dynamic reference: $identifier\n" +
            "Task dependency expressions cannot reference values defined inside this task.\n" +
            s"Move '$identifier' outside the task definition, or use Def.taskDyn to construct the dependent task."
        Result
          .assert(!compiled)
          .and(Result.assert(diagnostics.size == 1))
          .and(Result.assert(diagnostics.exists: diagnostic =>
            diagnostic.level == ERROR && diagnostic.message == expected &&
              diagnostic.location.contains((sourceName, line, column, identifier))))
          .log(diagnostics.mkString("\n"))
      }
    )
  ++ List(
    example(
      "an inline ScopeFilter compiles through the build evaluator", {
        val (compiled, diagnostics) = compile(
          "sources := sources.all(ScopeFilter(inProjects(ThisProject), inConfigurations(Compile))).value.flatten"
        )
        Result
          .assert(compiled)
          .and(Result.assert(diagnostics.isEmpty))
          .log(diagnostics.mkString("\n"))
      }
    ),
    example(
      "a task-local ScopeFilter receives an actionable diagnostic at its use", {
        val (compiled, diagnostics) = compile(
          """sources := {
          |  val filter = ScopeFilter(inProjects(ThisProject), inConfigurations(Compile))
          |  sources.all(filter).value.flatten
          |}""".stripMargin
        )
        val expected =
          "Illegal dynamic reference: filter\n" +
            "Task dependency expressions cannot reference values defined inside this task.\n" +
            "Move 'filter' outside the task definition, or use Def.taskDyn to construct the dependent task."
        Result
          .assert(!compiled)
          .and(Result.assert(diagnostics.size == 1))
          .and(Result.assert(diagnostics.exists: diagnostic =>
            diagnostic.level == ERROR && diagnostic.message == expected &&
              diagnostic.location.contains((sourceName, 2, 14, "filter"))))
          .log(diagnostics.mkString("\n"))
      }
    ),
  )

  private def compile(code: String): (Boolean, Vector[RecordedDiagnostic]) =
    val reporter = RecordingReporter()
    val evaluator = new Eval(Nil, classpath, backingDir = None, mkReporter = Some(() => reporter))
    val compiled =
      try
        evaluator.eval(code, imports, None, sourceName, 1)
        true
      catch case _: EvalException => false
    (compiled, reporter.diagnostics)

  private final case class RecordedDiagnostic(
      level: Int,
      message: String,
      location: Option[(String, Int, Int, String)]
  )

  private final class RecordingReporter extends EvalReporter:
    var diagnostics = Vector.empty[RecordedDiagnostic]

    override def doReport(diagnostic: Diagnostic)(using Context): Unit =
      val position = diagnostic.pos
      val location = Option.when(position.exists):
        val source = position.source
        val line = source.offsetToLine(position.start)
        (
          source.file.path,
          line,
          position.start - source.lineToOffset(line),
          new String(source.content.slice(position.start, position.end))
        )
      diagnostics :+= RecordedDiagnostic(diagnostic.level, diagnostic.msg.message, location)

    override def finalReport(sourceName: String): Unit = ()
  end RecordingReporter
end IllegalDynamicReferenceSpec
