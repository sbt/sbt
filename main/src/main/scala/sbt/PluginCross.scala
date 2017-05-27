/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 * Copyright 2012 Johannes Rudolph
 *
 * This was basically copied from the sbt source code and then adapted to use
 * `sbtVersion in pluginCrossBuild`.
 */
package sbt

import sbt.internal.util.complete.{ Parser, DefaultParsers }
import DefaultParsers._
import sbt.Keys._
import Project._
import Scope.GlobalScope
import Def.ScopedKey
import sbt.internal.Load
import sbt.internal.CommandStrings._
import Cross.{ spacedFirst, requireSession }

/**
 * Module responsible for plugin cross building.
 */
private[sbt] object PluginCross {
  lazy val pluginSwitch: Command = {
    def switchParser(state: State): Parser[(String, String)] = {
      val knownVersions = Nil
      lazy val switchArgs = token(NotSpace.examples(knownVersions: _*)) ~ (token(
        Space ~> matched(state.combinedParser)) ?? "")
      lazy val nextSpaced = spacedFirst(PluginSwitchCommand)
      token(PluginSwitchCommand ~ OptSpace) flatMap { _ =>
        switchArgs & nextSpaced
      }
    }
    def crossExclude(s: Def.Setting[_]): Boolean =
      s.key match {
        case ScopedKey(Scope(_, _, pluginCrossBuild.key, _), sbtVersion.key) => true
        case _                                                               => false
      }
    Command.arb(requireSession(switchParser), pluginSwitchHelp) {
      case (state, (version, command)) =>
        val x = Project.extract(state)
        import x._
        state.log.info(s"Setting `sbtVersion in pluginCrossBuild` to $version")
        val add = (sbtVersion in GlobalScope in pluginCrossBuild :== version) :: Nil
        val cleared = session.mergeSettings.filterNot(crossExclude)
        val newStructure = Load.reapply(cleared ++ add, structure)
        Project.setProject(session, newStructure, command :: state)
    }
  }

  lazy val pluginCross: Command = {
    def crossParser(state: State): Parser[String] =
      token(PluginCrossCommand <~ OptSpace) flatMap { _ =>
        token(
          matched(state.combinedParser &
            spacedFirst(PluginCrossCommand)))
      }
    def crossVersions(state: State): List[String] = {
      val x = Project.extract(state)
      import x._
      ((crossSbtVersions in currentRef) get structure.data getOrElse Nil).toList
    }
    Command.arb(requireSession(crossParser), pluginCrossHelp) {
      case (state, command) =>
        val x = Project.extract(state)
        import x._
        val versions = crossVersions(state)
        val current = (sbtVersion in pluginCrossBuild)
          .get(structure.data)
          .map(PluginSwitchCommand + " " + _)
          .toList
        if (versions.isEmpty) command :: state
        else versions.map(PluginSwitchCommand + " " + _ + " " + command) ::: current ::: state
    }
  }
}
