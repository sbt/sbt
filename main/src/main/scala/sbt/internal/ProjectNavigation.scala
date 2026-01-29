/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.net.URI
import sbt.internal.util.complete, complete.{ DefaultParsers, Parser }, DefaultParsers.*
import Keys.{ channelProjectCursors, sessionSettings }
import sbt.ProjectExtra.{ extract, updateCurrent }

object ProjectNavigation {
  def command(s: State): Parser[() => State] =
    if s.get(sessionSettings).isEmpty then failure("No project loaded")
    else (new ProjectNavigation(s)).command

  private[sbt] def getChannelName(s: State): Option[String] =
    s.currentCommand
      .flatMap(_.source)
      .map(_.channelName)
      .orElse(StandardMain.exchange.currentExec.flatMap(_.source).map(_.channelName))

  def getChannelCursor(s: State): Option[ProjectRef] =
    for {
      channelName <- getChannelName(s)
      cursors <- s.get(channelProjectCursors)
      ref <- cursors.get(channelName)
    } yield ref

  def setChannelCursor(s: State, ref: ProjectRef): State =
    getChannelName(s) match {
      case Some(channelName) =>
        val cursors = s.get(channelProjectCursors).getOrElse(Map.empty)
        s.put(channelProjectCursors, cursors.updated(channelName, ref))
      case None => s
    }

  def effectiveCurrentRef(s: State): ProjectRef =
    getChannelCursor(s).getOrElse(
      s.get(sessionSettings).map(_.current).getOrElse(sys.error("Session not initialized"))
    )
}

final class ProjectNavigation(s: State) {
  val extracted: Extracted = Project.extract(s)
  import extracted.{ structure, session }

  def effectiveCurrentRef: ProjectRef = ProjectNavigation.effectiveCurrentRef(s)

  def setProject(nuri: URI, nid: String): State = {
    val neval = if (effectiveCurrentRef.build == nuri) session.currentEval else mkEval(nuri)
    Project.updateCurrent(s.put(sessionSettings, session.setCurrent(nuri, nid, neval)))
  }

  def mkEval(nuri: URI): () => Eval = Load.lazyEval(structure.units(nuri).unit)
  def getRoot(uri: URI): String = Load.getRootProject(structure.units)(uri)

  def apply(action: Option[ResolvedReference]): State =
    action match {
      case None =>
        show(); s
      case Some(BuildRef(uri))       => changeBuild(uri)
      case Some(ProjectRef(uri, id)) => selectProject(uri, id)
    }

  def show(): Unit =
    s.log.info(s"${effectiveCurrentRef.project} (in build ${effectiveCurrentRef.build})")

  def selectProject(uri: URI, to: String): State =
    if (structure.units(uri).defined.contains(to))
      setProject(uri, to)
    else
      fail(
        s"Invalid project name '$to' in build $uri (type 'projects' to list available projects)."
      )

  def changeBuild(newBuild: URI): State =
    if (structure.units contains newBuild)
      setProject(newBuild, getRoot(newBuild))
    else
      fail("Invalid build unit '" + newBuild + "' (type 'projects' to list available builds).")

  def fail(msg: String): State = { s.log.error(msg); s.fail }

  import Parser.*, complete.Parsers.*

  val parser: Parser[Option[ResolvedReference]] = {
    val reference =
      Act.resolvedReference(structure.index.keyIndex, effectiveCurrentRef.build, success(()))
    val root = token('/' ^^^ rootRef)
    success(None) | some(token(Space) ~> (root | reference))
  }

  def rootRef = ProjectRef(effectiveCurrentRef.build, getRoot(effectiveCurrentRef.build))

  val command: Parser[() => State] = Command.applyEffect(parser)(apply)
}
