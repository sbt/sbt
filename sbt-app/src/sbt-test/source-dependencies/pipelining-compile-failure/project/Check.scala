package sbt.pipeliningcompilefailure

import java.util.concurrent.CancellationException
import sbt.*
import sbt.Keys.*
import scala.jdk.OptionConverters.*
import xsbti.CompileFailed

object Check:
  val checkCompileFailure = Command.command("checkCompileFailure"): s =>
    val extracted = Project.extract(s)
    val c = LocalProject("c")
    val result = extracted.runTaskUnhandled(c / Compile / compile, s).map(_._2)
    val inc = result match
      case Some(Result.Inc(inc)) => inc
      case other                 => sys.error(s"c / Compile / compile did not fail: $other")
    val exceptions = Incomplete.allExceptions(inc)
    val cancelled = exceptions.collect { case e: CancellationException => e }
    assert(cancelled.isEmpty, s"the evaluation was cancelled: $inc")
    val compileFailed = exceptions.collect { case e: CompileFailed => e }
    assert(compileFailed.nonEmpty, s"the result has no CompileFailed: $inc")
    val sources = compileFailed.flatMap(_.problems).flatMap(_.position.sourcePath.toScala)
    assert(sources.exists(_.endsWith("A.scala")), s"no problem in A.scala: $inc")
    s
end Check
