/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ AttributeKey, complete, Types }

import complete.{ DefaultParsers, Parser }
import DefaultParsers._
import Def.ScopedKey
import Types.idFun
import java.io.File
import Scope.Global
import sbt.ProjectExtra.*

object Inspect {
  sealed trait Mode
  final case class Details(actual: Boolean) extends Mode
  private[sbt] case object DependencyTreeMode extends Mode { override def toString = "tree" }
  private[sbt] case object UsesMode extends Mode { override def toString = "inspect" }
  private[sbt] case object DefinitionsMode extends Mode { override def toString = "definitions" }
  val DependencyTree: Mode = DependencyTreeMode
  val Uses: Mode = UsesMode
  val Definitions: Mode = DefinitionsMode

  def parser: State => Parser[() => String] =
    (s: State) =>
      spacedModeParser(s) flatMap { mode =>
        commandHandler(s, mode) | keyHandler(s)(mode)
      }
  val spacedModeParser: State => Parser[Mode] = (_: State) => {
    val default = "-" ^^^ Details(false)
    val actual = "actual" ^^^ Details(true)
    val tree = "tree" ^^^ DependencyTree
    val uses = "uses" ^^^ Uses
    val definitions = "definitions" ^^^ Definitions
    token(Space ~> (default | tree | actual | uses | definitions)) ?? Details(false)
  }

  def allKeyParser(s: State): Parser[AttributeKey[?]] = {
    val keyMap = Project.structure(s).index.keyMap
    token(Space ~> ((ID !!! "Expected key").examples(keyMap.keySet))).flatMap: key =>
      Act.getKey(keyMap, key, idFun)
  }
  val spacedKeyParser: State => Parser[ScopedKey[?]] = (s: State) =>
    Act.requireSession(s, token(Space) ~> Act.scopedKeyParser(s))

  def keyHandler(s: State): Mode => Parser[() => String] = {
    case opt @ (UsesMode | DefinitionsMode) =>
      allKeyParser(s).map(key => () => keyOutput(s, opt, Def.ScopedKey(Global, key)))
    case opt @ (DependencyTreeMode | Details(_)) =>
      spacedKeyParser(s).map(key => () => keyOutput(s, opt, key))
  }

  def commandHandler(s: State, mode: Mode): Parser[() => String] = {
    Space ~> commandParser(s).flatMap { case (name, cmd) =>
      cmd.tags.get(BasicCommands.CommandAliasKey) match {
        case Some((_, aliasFor)) =>
          def header = s"Alias for: $aliasFor"
          Parser
            .parse(" " ++ aliasFor, keyHandler(s)(mode))
            .fold(
              // If we can't find a task key for the alias target
              // we don't display anymore information
              _ => success(() => header),
              success
            )
        case None =>
          success(() => s"Command: $name")
      }
    }
  }

  def commandParser: State => Parser[(String, Command)] = { s =>
    oneOf(s.definedCommands.map(cmd => cmd -> cmd.nameOption) collect { case (cmd, Some(name)) =>
      DefaultParsers.literal(name).map(_ -> cmd)
    })
  }

  def keyOutput(s: State, option: Mode, sk: Def.ScopedKey[?]): String = {
    val extracted = Project.extract(s)
    import extracted.{ *, given }
    option match {
      case Details(actual) => Project.details(extracted.structure, actual, sk)
      case DependencyTreeMode =>
        val basedir = new File(Project.session(s).current.build)
        Project
          .settingGraph(extracted.structure, basedir, sk)
          .dependsAscii(get(sbt.Keys.asciiGraphWidth))
      case UsesMode =>
        Project.showUses(Project.usedBy(extracted.structure, true, sk.key))
      case DefinitionsMode =>
        Project.showDefinitions(sk.key, Project.definitions(extracted.structure, true, sk.key))
    }
  }

}
