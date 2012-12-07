/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import ProjectNavigation._
	import Project.updateCurrent
	import Keys.sessionSettings
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import java.net.URI

object ProjectNavigation
{
	def command(s: State): Parser[() => State] =
		if(s get sessionSettings isEmpty) failure("No project loaded") else (new ProjectNavigation(s)).command
}
final class ProjectNavigation(s: State)
{
	val extracted = Project extract s
		import extracted.{currentRef, structure, session}

	def setProject(nuri: URI, nid: String) =
	{
		val neval = if(currentRef.build == nuri) session.currentEval else mkEval(nuri)
		updateCurrent(s.put(sessionSettings, session.setCurrent(nuri, nid, neval)))
	}
	def mkEval(nuri: URI) = Load.lazyEval(structure.units(nuri).unit)
	def getRoot(uri: URI) = Load.getRootProject(structure.units)(uri)

	def apply(action: Option[ResolvedReference]): State =
		action match
		{
			case None => show(); s
			case Some(BuildRef(uri)) => changeBuild(uri)
			case Some(ProjectRef(uri, id)) => selectProject(uri, id)
/*			else if(to.forall(_ == '.'))
				if(to.length > 1) gotoParent(to.length - 1, nav, s) else s */ // semantics currently undefined
		}

	def show(): Unit  =  s.log.info(currentRef.project + " (in build " + currentRef.build + ")")
	def selectProject(uri: URI, to: String): State =
		if( structure.units(uri).defined.contains(to) )
			setProject(uri, to)
		else
			fail("Invalid project name '" + to + "' in build " + uri + " (type 'projects' to list available projects).")

	def changeBuild(newBuild: URI): State =
		if(structure.units contains newBuild)
			setProject(newBuild, getRoot(newBuild))
		else
			fail("Invalid build unit '" + newBuild + "' (type 'projects' to list available builds).")

	def fail(msg: String): State =
	{
		s.log.error(msg)
		s.fail
	}

		import complete.Parser._
		import complete.Parsers._

	val parser: Parser[Option[ResolvedReference]] =
	{
		val reference = Act.resolvedReference(structure.index.keyIndex, currentRef.build, success(()))
		val root = token('/' ^^^ rootRef)
		success(None) | some( token(Space) ~> (root | reference) )
	}
	def rootRef = ProjectRef(currentRef.build, getRoot(currentRef.build))
	val command: Parser[() => State] = Command.applyEffect(parser)(apply)
}