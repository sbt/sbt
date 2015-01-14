/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

import Keys._
import complete.{ DefaultParsers, Parser }
import DefaultParsers._
import Def.{ ScopedKey, Setting }
import Scope.GlobalScope
import CommandStrings.{ CrossCommand, crossHelp, SwitchCommand, switchHelp }
import java.io.File

object Cross {
  @deprecated("Moved to CommandStrings.Switch", "0.13.0")
  final val Switch = CommandStrings.SwitchCommand

  @deprecated("Moved to CommandStrings.Cross", "0.13.0")
  final val Cross = CommandStrings.CrossCommand

  def switchParser(state: State): Parser[(String, String)] =
    {
      def versionAndCommand(spacePresent: Boolean) = {
        val knownVersions = crossVersions(state)
        val version = token(StringBasic.examples(knownVersions: _*))
        val spacedVersion = if (spacePresent) version else version & spacedFirst(SwitchCommand)
        val optionalCommand = token(Space ~> matched(state.combinedParser)) ?? ""
        spacedVersion ~ optionalCommand
      }
      token(SwitchCommand ~> OptSpace) flatMap { sp => versionAndCommand(sp.nonEmpty) }
    }
  def spacedFirst(name: String) = opOrIDSpaced(name) ~ any.+

  lazy val switchVersion = Command.arb(requireSession(switchParser), switchHelp) {
    case (state, (arg, command)) =>
      val x = Project.extract(state)
      import x._

      val (resolveVersion, homePath) = arg.split("=") match {
        case Array(v, h) => (v, h)
        case _           => ("", arg)
      }
      val home = IO.resolve(x.currentProject.base, new File(homePath))
      // Basic Algorithm.
      // 1. First we figure out what the new scala instances should be, create settings for them.
      // 2. Find any non-overridden scalaVersion setting in the whole build and force it to delegate
      //    to the new global settings.
      // 3. Append these to the session, so that the session is up-to-date and
      //    things like set/session clear, etc. work.
      val (add, exclude) =
        if (home.exists) {
          val instance = ScalaInstance(home)(state.classLoaderCache.apply _)
          state.log.info("Setting Scala home to " + home + " with actual version " + instance.actualVersion)
          val version = if (resolveVersion.isEmpty) instance.actualVersion else resolveVersion
          state.log.info("\tand using " + version + " for resolving dependencies.")
          val settings = Seq(
            scalaVersion in GlobalScope :== version,
            scalaHome in GlobalScope :== Some(home),
            scalaInstance in GlobalScope :== instance
          )
          (settings, excludeKeys(Set(scalaVersion.key, scalaHome.key, scalaInstance.key)))
        } else if (!resolveVersion.isEmpty) {
          sys.error("Scala home directory did not exist: " + home)
        } else {
          state.log.info("Setting version to " + arg)
          val settings = Seq(
            scalaVersion in GlobalScope :== arg,
            scalaHome in GlobalScope :== None
          )
          (settings, excludeKeys(Set(scalaVersion.key, scalaHome.key)))
        }
      // TODO - Track delegates and avoid regenerating.
      val delegates: Seq[Setting[_]] = session.mergeSettings collect {
        case x if exclude(x) => delegateToGlobal(x.key)
      }
      val fixedSession = session.appendRaw(add ++ delegates)
      val fixedState = BuiltinCommands.reapply(fixedSession, structure, state)
      if (!command.isEmpty) command :: fixedState
      else fixedState
  }

  // Creates a delegate for a scoped key that pulls the setting from the global scope.
  private[this] def delegateToGlobal[T](key: ScopedKey[T]): Setting[_] =
    SettingKey[T](key.key) in key.scope := (SettingKey[T](key.key) in GlobalScope).value

  @deprecated("No longer used.", "0.13.0")
  def crossExclude(s: Setting[_]): Boolean = excludeKeys(Set(scalaVersion.key, scalaHome.key))(s)

  private[this] def excludeKeys(keys: Set[AttributeKey[_]]): Setting[_] => Boolean =
    _.key match {
      case ScopedKey(Scope(_, Global, Global, _), key) if keys.contains(key) => true
      case _ => false
    }

  def crossParser(state: State): Parser[String] =
    token(CrossCommand <~ OptSpace) flatMap { _ => token(matched(state.combinedParser & spacedFirst(CrossCommand))) }

  lazy val crossBuild = Command.arb(requireSession(crossParser), crossHelp) { (state, command) =>
    val x = Project.extract(state)
    import x._
    val versions = crossVersions(state)
    val current = scalaVersion in currentRef get structure.data map (SwitchCommand + " " + _) toList;
    if (versions.isEmpty) command :: state
    else {
      versions.map(v => s"$SwitchCommand $v $command") ::: current ::: state
    }
  }
  def crossVersions(state: State): Seq[String] =
    {
      val x = Project.extract(state)
      import x._
      crossScalaVersions in currentRef get structure.data getOrElse Nil
    }

  def requireSession[T](p: State => Parser[T]): State => Parser[T] = s =>
    if (s get sessionSettings isEmpty) failure("No project loaded") else p(s)

}