/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Keys._
	import complete.{DefaultParsers, Parser}
	import DefaultParsers._
	import Def.{ScopedKey, Setting}
	import Scope.GlobalScope
	import CommandStrings.{CrossCommand,crossHelp,SwitchCommand,switchHelp}
	import java.io.File

object Cross
{
	@deprecated("Moved to CommandStrings.Switch", "0.13.0")
	final val Switch = CommandStrings.SwitchCommand

	@deprecated("Moved to CommandStrings.Cross", "0.13.0")
	final val Cross = CommandStrings.CrossCommand

	def switchParser(state: State): Parser[(String, String)] =
	{
		def versionAndCommand(spacePresent: Boolean) = {
			val knownVersions = crossVersions(state)
			val version = token(StringBasic.examples(knownVersions : _*))
			val spacedVersion = if(spacePresent) version else version & spacedFirst(SwitchCommand)
			val optionalCommand = token(Space ~> matched(state.combinedParser)) ?? ""
			spacedVersion ~ optionalCommand
		}
		token(SwitchCommand ~> OptSpace) flatMap { sp => versionAndCommand(!sp.isEmpty) }
	}
	def spacedFirst(name: String) = opOrIDSpaced(name) ~ any.+

	lazy val switchVersion = Command.arb(requireSession(switchParser), switchHelp) { case (state, (version, command)) =>
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
		token(CrossCommand <~ OptSpace) flatMap { _ => token(matched( state.combinedParser & spacedFirst(CrossCommand) )) }

	lazy val crossBuild = Command.arb(requireSession(crossParser), crossHelp) { (state, command) =>
		val x = Project.extract(state)
			import x._
		val versions = crossVersions(state)
		val current = scalaVersion in currentRef get structure.data map(SwitchCommand + " " + _) toList;
		if(versions.isEmpty) command :: state else versions.map(SwitchCommand + " " + _ + " " + command) ::: current ::: state
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