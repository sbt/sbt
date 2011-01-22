/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package sbt

	import ProjectNavigation._
	import Project.{SessionKey, updateCurrent}
	import CommandSupport.logger
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import java.net.URI

object ProjectNavigation
{
	sealed trait Navigate
	final object ShowCurrent extends Navigate
	final object Root extends Navigate
	final class ChangeBuild(val base: URI) extends Navigate
	final class ChangeProject(val id: String) extends Navigate

	def command(s: State): Parser[() => State] =
		if(s get Project.SessionKey isEmpty) failure("No project loaded") else (new ProjectNavigation(s)).command
}
final class ProjectNavigation(s: State)
{
	val session = Project session s
	val structure = Project structure s
	val builds = structure.units.keys.toSet
	val (uri, pid) = session.current
	val projects = Load.getBuild(structure.units, uri).defined.keys

	def setProject(uri: URI, id: String) = updateCurrent(s.put(SessionKey, session.setCurrent(uri, id)))
	def getRoot(uri: URI) = Load.getRootProject(structure.units)(uri)

	def apply(action: Navigate): State =
		action match
		{
			case ShowCurrent => show(); s
			case Root => setProject(uri, getRoot(uri))
			case b: ChangeBuild => changeBuild(b.base)
/*			else if(to.forall(_ == '.'))
				if(to.length > 1) gotoParent(to.length - 1, nav, s) else s */ // semantics currently undefined
			case s: ChangeProject => selectProject(s.id)
		}

	def show(): Unit  =  logger(s).info(pid + " (in build " + uri + ")")
	def selectProject(to: String): State =
		if( structure.units(uri).defined.contains(to) )
			setProject(uri, to)
		else
			fail("Invalid project name '" + to + "' (type 'projects' to list available projects).")

	def changeBuild(to: URI): State =
	{	
		val newBuild = (uri resolve to).normalize
		if(structure.units contains newBuild)
			setProject(newBuild, getRoot(newBuild))
		else
			fail("Invalid build unit '" + newBuild + "' (type 'projects' to list available builds).")
	}
	def fail(msg: String): State =
	{
		logger(s).error(msg)
		s.fail
	}

		import complete.Parser._
		import complete.Parsers._

	val parser: Parser[Navigate] =
	{
		val buildP = token('^') ~> token(Uri(builds) map(u => new ChangeBuild(u) ) )
		val projectP = token(ID map (id => new ChangeProject(id)) examples projects.toSet )
		success(ShowCurrent) | ( token(Space) ~> (token('/' ^^^ Root) | buildP | projectP) )
	}
	val command: Parser[() => State] = Commands.applyEffect(parser)(apply)
}