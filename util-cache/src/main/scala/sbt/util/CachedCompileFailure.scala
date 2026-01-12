/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.{ Builder, JsonFormat, Unbuilder, deserializationError }
import xsbti.{ CompileFailed, Problem, Position, Severity }
import java.util.Optional

/**
 * A serializable representation of a CompileFailed exception.
 * This allows caching compilation failures so that repeated builds
 * don't re-run failed compilations unnecessarily.
 *
 * Fixes https://github.com/sbt/sbt/issues/7662
 */
final case class CachedCompileFailure(
    problems: Vector[CachedProblem],
    message: String
):
  def toException: CompileFailed = new CompileFailed:
    override def arguments(): Array[String] = Array.empty
    override def problems(): Array[Problem] = CachedCompileFailure.this.problems
      .map(_.toProblem)
      .toArray
    override def getMessage(): String = CachedCompileFailure.this.message
end CachedCompileFailure

object CachedCompileFailure:
  def fromException(e: CompileFailed): CachedCompileFailure =
    CachedCompileFailure(
      problems = e.problems().map(CachedProblem.fromProblem).toVector,
      message = Option(e.getMessage).getOrElse("")
    )

  given JsonFormat[CachedCompileFailure] = new JsonFormat[CachedCompileFailure]:
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CachedCompileFailure =
      jsOpt match
        case Some(js) =>
          unbuilder.beginObject(js)
          val problems = unbuilder.readField[Vector[CachedProblem]]("problems")
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

/**
 * A serializable representation of a compiler Problem.
 */
final case class CachedProblem(
    category: String,
    severity: String,
    message: String,
    position: CachedPosition,
    rendered: Option[String]
):
  def toProblem: Problem = new Problem:
    override def category(): String = CachedProblem.this.category
    override def severity(): Severity = CachedProblem.this.severity match
      case "Error" => Severity.Error
      case "Warn"  => Severity.Warn
      case _       => Severity.Info
    override def message(): String = CachedProblem.this.message
    override def position(): Position = CachedProblem.this.position.toPosition
    override def rendered(): Optional[String] =
      CachedProblem.this.rendered.map(Optional.of).getOrElse(Optional.empty())
end CachedProblem

object CachedProblem:
  def fromProblem(p: Problem): CachedProblem =
    CachedProblem(
      category = p.category(),
      severity = p.severity().toString,
      message = p.message(),
      position = CachedPosition.fromPosition(p.position()),
      rendered = if p.rendered().isPresent then Some(p.rendered().get()) else None
    )

  given JsonFormat[CachedProblem] = new JsonFormat[CachedProblem]:
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CachedProblem =
      jsOpt match
        case Some(js) =>
          unbuilder.beginObject(js)
          val category = unbuilder.readField[String]("category")
          val severity = unbuilder.readField[String]("severity")
          val message = unbuilder.readField[String]("message")
          val position = unbuilder.readField[CachedPosition]("position")
          val rendered = unbuilder.readField[Option[String]]("rendered")
          unbuilder.endObject()
          CachedProblem(category, severity, message, position, rendered)
        case None =>
          deserializationError("Expected JsObject but found None")

    override def write[J](obj: CachedProblem, builder: Builder[J]): Unit =
      builder.beginObject()
      builder.addField("category", obj.category)
      builder.addField("severity", obj.severity)
      builder.addField("message", obj.message)
      builder.addField("position", obj.position)
      builder.addField("rendered", obj.rendered)
      builder.endObject()
end CachedProblem

/**
 * A serializable representation of a compiler Position.
 */
final case class CachedPosition(
    line: Option[Int],
    lineContent: String,
    offset: Option[Int],
    pointer: Option[Int],
    pointerSpace: Option[String],
    sourcePath: Option[String],
    sourceFile: Option[String]
):
  def toPosition: Position = new Position:
    override def line(): Optional[Integer] =
      CachedPosition.this.line.map(Integer.valueOf).map(Optional.of).getOrElse(Optional.empty())
    override def lineContent(): String = CachedPosition.this.lineContent
    override def offset(): Optional[Integer] =
      CachedPosition.this.offset.map(Integer.valueOf).map(Optional.of).getOrElse(Optional.empty())
    override def pointer(): Optional[Integer] =
      CachedPosition.this.pointer.map(Integer.valueOf).map(Optional.of).getOrElse(Optional.empty())
    override def pointerSpace(): Optional[String] =
      CachedPosition.this.pointerSpace.map(Optional.of).getOrElse(Optional.empty())
    override def sourcePath(): Optional[String] =
      CachedPosition.this.sourcePath.map(Optional.of).getOrElse(Optional.empty())
    override def sourceFile(): Optional[java.io.File] =
      CachedPosition.this.sourceFile
        .map(p => new java.io.File(p))
        .map(Optional.of)
        .getOrElse(Optional.empty())
end CachedPosition

object CachedPosition:
  def fromPosition(p: Position): CachedPosition =
    CachedPosition(
      line = if p.line().isPresent then Some(p.line().get()) else None,
      lineContent = p.lineContent(),
      offset = if p.offset().isPresent then Some(p.offset().get()) else None,
      pointer = if p.pointer().isPresent then Some(p.pointer().get()) else None,
      pointerSpace = if p.pointerSpace().isPresent then Some(p.pointerSpace().get()) else None,
      sourcePath = if p.sourcePath().isPresent then Some(p.sourcePath().get()) else None,
      sourceFile = if p.sourceFile().isPresent then Some(p.sourceFile().get().getPath) else None
    )

  given JsonFormat[CachedPosition] = new JsonFormat[CachedPosition]:
    override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): CachedPosition =
      jsOpt match
        case Some(js) =>
          unbuilder.beginObject(js)
          val line = unbuilder.readField[Option[Int]]("line")
          val lineContent = unbuilder.readField[String]("lineContent")
          val offset = unbuilder.readField[Option[Int]]("offset")
          val pointer = unbuilder.readField[Option[Int]]("pointer")
          val pointerSpace = unbuilder.readField[Option[String]]("pointerSpace")
          val sourcePath = unbuilder.readField[Option[String]]("sourcePath")
          val sourceFile = unbuilder.readField[Option[String]]("sourceFile")
          unbuilder.endObject()
          CachedPosition(line, lineContent, offset, pointer, pointerSpace, sourcePath, sourceFile)
        case None =>
          deserializationError("Expected JsObject but found None")

    override def write[J](obj: CachedPosition, builder: Builder[J]): Unit =
      builder.beginObject()
      builder.addField("line", obj.line)
      builder.addField("lineContent", obj.lineContent)
      builder.addField("offset", obj.offset)
      builder.addField("pointer", obj.pointer)
      builder.addField("pointerSpace", obj.pointerSpace)
      builder.addField("sourcePath", obj.sourcePath)
      builder.addField("sourceFile", obj.sourceFile)
      builder.endObject()
end CachedPosition
