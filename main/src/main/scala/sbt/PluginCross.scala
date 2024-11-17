/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.complete.{ Parser, DefaultParsers }
import DefaultParsers._
import sbt.Keys._
import Scope.GlobalScope
import Def.ScopedKey
import sbt.SlashSyntax0.given
import sbt.internal.Load
import sbt.internal.CommandStrings._
import Cross.{ spacedFirst, requireSession }
import sbt.librarymanagement.VersionNumber
import Project.inScope
import ProjectExtra.{ extract, setProject }

/**
 * Module responsible for plugin cross building.
 */
private[sbt] object PluginCross {
  lazy val pluginSwitch: Command = {
    def switchParser(state: State): Parser[(String, String)] = {
      lazy val switchArgs = token(NotSpace.examples()) ~ (token(
        Space ~> matched(state.combinedParser)
      ) ?? "")
      lazy val nextSpaced = spacedFirst(PluginSwitchCommand)
      token(PluginSwitchCommand ~ OptSpace) flatMap { _ =>
        switchArgs & nextSpaced
      }
    }

    def crossExclude(s: Def.Setting[?]): Boolean =
      s.key match {
        case ScopedKey(Scope(_, _, pluginCrossBuild.key, _), sbtVersion.key) => true
        case _                                                               => false
      }

    Command.arb(requireSession(switchParser), pluginSwitchHelp) {
      case (state, (version, command)) =>
        val x = Project.extract(state)
        import x.{ *, given }
        state.log.info(s"Setting `pluginCrossBuild / sbtVersion` to $version")
        val add = List(GlobalScope / pluginCrossBuild / sbtVersion :== version) ++
          List(scalaVersion := scalaVersionSetting.value) ++
          inScope(GlobalScope.copy(project = Select(currentRef)))(
            Seq(scalaVersion := scalaVersionSetting.value)
          )
        val cleared = session.mergeSettings.filterNot(crossExclude)
        val newStructure = Load.reapply(cleared ++ add, structure)
        Project.setProject(session, newStructure, command :: state)
    }
  }

  lazy val pluginCross: Command = {
    def crossParser(state: State): Parser[String] =
      token(PluginCrossCommand <~ OptSpace) flatMap { _ =>
        token(
          matched(
            state.combinedParser &
              spacedFirst(PluginCrossCommand)
          )
        )
      }
    def crossVersions(state: State): List[String] = {
      val x = Project.extract(state)
      import x._
      (currentRef / crossSbtVersions)
        .get(structure.data)
        .getOrElse(Nil)
        .toList
    }
    Command.arb(requireSession(crossParser), pluginCrossHelp) { case (state, command) =>
      val x = Project.extract(state)
      import x._
      val versions = crossVersions(state)
      val current = (pluginCrossBuild / sbtVersion)
        .get(structure.data)
        .map(PluginSwitchCommand + " " + _)
        .toList
      if (versions.isEmpty) command :: state
      else versions.map(PluginSwitchCommand + " " + _ + " " + command) ::: current ::: state
    }
  }

  def scalaVersionSetting: Def.Initialize[String] = Def.setting {
    val scalaV = scalaVersion.value
    val sv = (pluginCrossBuild / sbtBinaryVersion).value
    val isPlugin = sbtPlugin.value
    if (isPlugin) scalaVersionFromSbtBinaryVersion(sv)
    else scalaV
  }

  def scalaVersionFromSbtBinaryVersion(sv: String): String =
    VersionNumber(sv) match {
      case VersionNumber(Seq(0, 12, _*), _, _) => "2.9.2"
      case VersionNumber(Seq(0, 13, _*), _, _) => "2.10.7"
      case VersionNumber(Seq(1, 0, _*), _, _)  => "2.12.20"
      case _                                   => sys.error(s"Unsupported sbt binary version: $sv")
    }
}
