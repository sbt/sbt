package sbt.pipeliningcompilefailure

import java.util.concurrent.CancellationException
import sbt._
import sbt.Keys._
import xsbti.CompileFailed

object Check {
  val checkCompileFailure = Command.command("checkCompileFailure") { s =>
    val key = (LocalProject("c") / Compile / compile).scopedKey
    val inc = Project.runTask(key, s).map(_._2) match {
      case Some(Inc(inc)) => inc
      case other          => sys.error(s"c / Compile / compile did not fail: $other")
    }
    val exceptions = Incomplete.allExceptions(inc)
    val cancelled = exceptions.collect { case e: CancellationException => e }
    assert(cancelled.isEmpty, s"the evaluation was cancelled: $inc")
    val compileFailed = exceptions.collect { case e: CompileFailed => e }
    assert(compileFailed.nonEmpty, s"the result has no CompileFailed: $inc")
    val sources = compileFailed
      .flatMap(_.problems.toSeq)
      .flatMap(p => Option(p.position.sourcePath.orElse(null)))
    assert(sources.exists(_.endsWith("A.scala")), s"no problem in A.scala: $inc")
    s
  }
}
