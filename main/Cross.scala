/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Keys._
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import Project.Setting
	import Scope.GlobalScope

object Cross
{
	final val Switch = "++"
	final val Cross = "+"

	def switchParser(state: State): Parser[(String, String)] =
	{
		val knownVersions = crossVersions(state)
		lazy val switchArgs = token(OptSpace ~> NotSpace.examples(knownVersions : _*)) ~ (token(Space ~> matched(state.combinedParser)) ?? "")
		lazy val nextSpaced = spacedFirst(Switch)
		token(Switch) flatMap { _ => switchArgs & nextSpaced }
	}
	def spacedFirst(name: String) = opOrIDSpaced(name) ~ any.+

	lazy val switchVersion = Command.arb(requireSession(switchParser)) { case (state, (version, command)) =>
		val x = Project.extract(state)
			import x._
		println("Setting version to " + version)
		val add = (scalaVersion in GlobalScope :== version) :: (scalaHome in GlobalScope :== None) :: Nil
		val cleared = session.mergeSettings.filterNot( crossExclude )
		val newStructure = Load.reapply(add ++ cleared, structure)
		Project.setProject(session, newStructure, command :: state)
	}
	def crossExclude(s: Setting[_]): Boolean =
		s.key.key == scalaVersion.key || s.key.key == scalaHome.key

	def crossParser(state: State): Parser[String] =
		token(Cross <~ OptSpace) flatMap { _ => token(matched( state.combinedParser & spacedFirst(Cross) )) }

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