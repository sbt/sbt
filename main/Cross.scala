/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Keys._
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._


object Cross
{
	final val Switch = "++"
	final val Cross = "+"

	def switchParser(state: State): Parser[(String, String)] =
	{
		val knownVersions = crossVersions(state)
		token(Switch ~ Space) flatMap { _ => token(NotSpace.examples(knownVersions : _*)) ~ (token(Space ~> matched(state.combinedParser)) ?? "") }
	}
	lazy val switchVersion = Command.arb(requireSession(switchParser)) { case (state, (version, command)) =>
		val x = Project.extract(state)
			import x._
		val add = (scalaVersion :== version) :: (scalaHome :== None) :: Nil
		val append = Load.transformSettings(Load.projectScope(currentRef), currentRef.build, rootProject, add)
		val newStructure = Load.reapply(session.original ++ append, structure)
		Project.setProject(session, newStructure, command :: state)
	}

	def crossParser(state: State): Parser[String] =
		token(Cross ~ Space) flatMap { _ => token(matched(state.combinedParser)) }

	lazy val crossBuild = Command.arb(requireSession(crossParser)) { (state, command) =>
		val x = Project.extract(state)
			import x._
		val versions = crossVersions(state)
		val current = scalaVersion in currentRef get structure.data map(Switch + " " + _) toList;
		if(versions.isEmpty) command :: state else versions.map(Switch + " " + _ + " " + command) ::: current ::: state
	}
	def crossVersions(state: State): Seq[String] =
	{
		val x = Project.extract(state)
			import x._
		crossScalaVersions in currentRef get structure.data getOrElse Nil
	}
	
	def requireSession[T](p: State => Parser[T]): State => Parser[T] = s =>
		if(s get sessionSettings isEmpty) failure("No project loaded") else p(s)

}