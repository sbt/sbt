/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Keys._
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import Def.{ScopedKey, Setting}
	import Scope.GlobalScope
	import java.io.File

object Cross
{
	final val Switch = "++"
	final val Cross = "+"

	def switchParser(state: State): Parser[(String, String)] =
	{
		def versionAndCommand(spacePresent: Boolean) = {
			val knownVersions = crossVersions(state)
			val version = token(StringBasic.examples(knownVersions : _*))
			val spacedVersion = if(spacePresent) version else version & spacedFirst(Switch)
			val optionalCommand = token(Space ~> matched(state.combinedParser)) ?? ""
			spacedVersion ~ optionalCommand
		}
		token(Switch ~> OptSpace) flatMap { sp => versionAndCommand(!sp.isEmpty) }
	}
	def spacedFirst(name: String) = opOrIDSpaced(name) ~ any.+

	lazy val switchVersion = Command.arb(requireSession(switchParser)) { case (state, (version, command)) =>
		val x = Project.extract(state)
			import x._
		val home = IO.resolve(x.currentProject.base, new File(version))
		val (add, exclude) = 
			if(home.exists) {
				val instance = ScalaInstance(home)(state.classLoaderCache.apply _)
				state.log.info("Setting Scala home to " + home + " with actual version " + instance.actualVersion)
				val settings = Seq(
					scalaVersion in GlobalScope :== instance.actualVersion,
					scalaHome in GlobalScope :== Some(home),
					scalaInstance in GlobalScope :== instance
				)
				(settings, excludeKeys(Set(scalaVersion.key, scalaHome.key, scalaInstance.key)))
			} else {
				state.log.info("Setting version to " + version)
				val settings = Seq(
					scalaVersion in GlobalScope :== version,
					scalaHome in GlobalScope :== None
				)
				(settings, excludeKeys(Set(scalaVersion.key, scalaHome.key)))
			}
		val cleared = session.mergeSettings.filterNot( exclude )
		val newStructure = Load.reapply(add ++ cleared, structure)
		Project.setProject(session, newStructure, command :: state)
	}
	@deprecated("No longer used.", "0.13.0")
	def crossExclude(s: Setting[_]): Boolean = excludeKeys(Set(scalaVersion.key, scalaHome.key))(s)

	private[this] def excludeKeys(keys: Set[AttributeKey[_]]): Setting[_] => Boolean =
		_.key match {
			case ScopedKey( Scope(_, Global, Global, _), key) if keys.contains(key) => true
			case _ => false
		}

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