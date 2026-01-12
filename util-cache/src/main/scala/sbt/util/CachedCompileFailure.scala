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
import xsbti.{ CompileFailed, Problem }

/**
 * A serializable representation of a CompileFailed exception.
 * This allows caching compilation failures so that repeated builds
 * don't re-run failed compilations unnecessarily.
 *
 * Fixes https://github.com/sbt/sbt/issues/7662
 */
final case class CachedCompileFailure(
    problems: Vector[Problem],
    message: String
):
  def toException: CompileFailed = new CompileFailed:
    override def arguments(): Array[String] = Array.empty
    override def problems(): Array[Problem] = CachedCompileFailure.this.problems.toArray
    override def getMessage(): String = CachedCompileFailure.this.message
end CachedCompileFailure

object CachedCompileFailure
    extends ProblemFormats
    with SeverityFormats
    with PositionFormats
    with sjsonnew.BasicJsonProtocol:

  def fromException(e: CompileFailed): CachedCompileFailure =
    CachedCompileFailure(
      problems = e.problems().toVector,
      message = Option(e.getMessage).getOrElse("")
    )

  given JsonFormat[CachedCompileFailure] = new JsonFormat[CachedCompileFailure]:
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CachedCompileFailure =
      jsOpt match
        case Some(js) =>
          unbuilder.beginObject(js)
          val problems = unbuilder.readField[Vector[Problem]]("problems")
          val message = unbuilder.readField[String]("message")
          unbuilder.endObject()
          CachedCompileFailure(problems, message)
        case None =>
          deserializationError("Expected JsObject but found None")

    override def write[J](obj: CachedCompileFailure, builder: Builder[J]): Unit =
      builder.beginObject()
      builder.addField("problems", obj.problems)
      builder.addField("message", obj.message)
      builder.endObject()
end CachedCompileFailure
