/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import java.net.URI
import sbt.internal.util.complete, complete.{ DefaultParsers, Parser }, DefaultParsers._
import sbt.compiler.Eval
import Keys.sessionSettings
import Project.updateCurrent

object ProjectNavigation {
  def command(s: State): Parser[() => State] =
    if (s get sessionSettings isEmpty) failure("No project loaded")
    else (new ProjectNavigation(s)).command
}

final class ProjectNavigation(s: State) {
  val extracted: Extracted = Project extract s
  import extracted.{ currentRef, structure, session }

  def setProject(nuri: URI, nid: String): State = {
    val neval = if (currentRef.build == nuri) session.currentEval else mkEval(nuri)
    updateCurrent(s.put(sessionSettings, session.setCurrent(nuri, nid, neval)))
  }

  def mkEval(nuri: URI): () => Eval = Load.lazyEval(structure.units(nuri).unit)
  def getRoot(uri: URI): String = Load.getRootProject(structure.units)(uri)

  def apply(action: Option[ResolvedReference]): State =
    action match {
      case None =>
        show(); s
      case Some(BuildRef(uri))       => changeBuild(uri)
      case Some(ProjectRef(uri, id)) => selectProject(uri, id)
      /*			else if(to.forall(_ == '.'))
				if(to.length > 1) gotoParent(to.length - 1, nav, s) else s */ // semantics currently undefined
    }

  def show(): Unit = s.log.info(s"${currentRef.project} (in build ${currentRef.build})")

  def selectProject(uri: URI, to: String): State =
    if (structure.units(uri).defined.contains(to))
      setProject(uri, to)
    else
      fail(
        s"Invalid project name '$to' in build $uri (type 'projects' to list available projects).")

  def changeBuild(newBuild: URI): State =
    if (structure.units contains newBuild)
      setProject(newBuild, getRoot(newBuild))
    else
      fail("Invalid build unit '" + newBuild + "' (type 'projects' to list available builds).")

  def fail(msg: String): State = { s.log.error(msg); s.fail }

  import Parser._, complete.Parsers._

  val parser: Parser[Option[ResolvedReference]] = {
    val reference = Act.resolvedReference(structure.index.keyIndex, currentRef.build, success(()))
    val root = token('/' ^^^ rootRef)
    success(None) | some(token(Space) ~> (root | reference))
  }

  def rootRef = ProjectRef(currentRef.build, getRoot(currentRef.build))

  val command: Parser[() => State] = Command.applyEffect(parser)(apply)
}
