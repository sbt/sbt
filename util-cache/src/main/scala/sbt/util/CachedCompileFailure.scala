/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import sbt.internal.util.codec.{ ProblemFormats, SeverityFormats, PositionFormats }
import xsbti.{ CompileFailed, Problem, Severity }

/**
 * A wrapper around GenericFailure for CompileFailed exceptions.
 * This allows caching compilation failures so that repeated builds
 * don't re-run failed compilations unnecessarily.
 *
 * Fixes https://github.com/sbt/sbt/issues/7662
 */
final case class CachedCompileFailure(underlying: GenericFailure):
  def toException: CompileFailed = new CompileFailed:
    override def arguments(): Array[String] = Array.empty
    override def problems(): Array[Problem] = underlying.problems.toArray
    override def getMessage(): String = underlying.message.getOrElse("")

  /** Replay problems to the logger so users see the cached errors/warnings. */
  def replay(logger: Logger): Unit =
    underlying.problems.foreach: problem =>
      val msg = CachedCompileFailure.formatProblem(problem)
      problem.severity match
        case Severity.Error => logger.error(msg)
        case Severity.Warn  => logger.warn(msg)
        case Severity.Info  => logger.info(msg)
end CachedCompileFailure

object CachedCompileFailure
    extends ProblemFormats
    with SeverityFormats
    with PositionFormats
    with sjsonnew.BasicJsonProtocol:

  private val CompileFailedKind = "CompileFailed"

  /**
   * Format a problem for display. Uses the `rendered` field if available (Scala 3),
   * otherwise constructs a message from position and message (Scala 2.13).
   */
  private[util] def formatProblem(problem: Problem): String =
    import sbt.util.InterfaceUtil.toOption
    toOption(problem.rendered).getOrElse:
      val pos = problem.position
      val file = toOption(pos.sourcePath).getOrElse("unknown")
      val line = toOption(pos.line).map(l => s":$l").getOrElse("")
      val pointer = toOption(pos.pointer).map(p => s":$p").getOrElse("")
      val lineContent = Option(pos.lineContent).filter(_.nonEmpty).map(c => s"\n$c").getOrElse("")
      val pointerLine = toOption(pos.pointerSpace).map(s => s"\n$s^").getOrElse("")
      s"$file$line$pointer: ${problem.message}$lineContent$pointerLine"

  /**
   * Check if the problems contain enough information to be useful when replayed.
   * For Scala 2.13, the `rendered` field is empty, so we check if position info exists.
   */
  def hasSufficientInfo(e: CompileFailed): Boolean =
    import sbt.util.InterfaceUtil.toOption
    e.problems()
      .forall: problem =>
        // Either has rendered text (Scala 3) or has position info (Scala 2.13)
        toOption(problem.rendered).isDefined ||
          (toOption(problem.position.sourcePath).isDefined && problem.message.nonEmpty)

  def fromException(e: CompileFailed): CachedCompileFailure =
    CachedCompileFailure(
      GenericFailure(
        kind = CompileFailedKind,
        message = Option(e.getMessage).getOrElse(""),
        problems = e.problems().toVector
      )
    )

  // Custom JsonFormat for GenericFailure since we disabled automatic codec generation
  given JsonFormat[GenericFailure] = new JsonFormat[GenericFailure]:
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): GenericFailure =
      jsOpt match
        case Some(js) =>
          unbuilder.beginObject(js)
          val kind = unbuilder.readField[Option[String]]("kind")
          val message = unbuilder.readField[Option[String]]("message")
          val problems = unbuilder.readField[Vector[Problem]]("problems")
          unbuilder.endObject()
          GenericFailure(kind, message, problems)
        case None =>
          deserializationError("Expected JsObject but found None")

    override def write[J](obj: GenericFailure, builder: Builder[J]): Unit =
      builder.beginObject()
      builder.addField("kind", obj.kind)
      builder.addField("message", obj.message)
      builder.addField("problems", obj.problems)
      builder.endObject()

  given JsonFormat[CachedCompileFailure] = new JsonFormat[CachedCompileFailure]:
    private val gf = summon[JsonFormat[GenericFailure]]
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CachedCompileFailure =
      CachedCompileFailure(gf.read(jsOpt, unbuilder))
    override def write[J](obj: CachedCompileFailure, builder: Builder[J]): Unit =
      gf.write(obj.underlying, builder)
end CachedCompileFailure
