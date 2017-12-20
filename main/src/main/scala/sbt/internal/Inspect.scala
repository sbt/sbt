/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
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

object Inspect {
  sealed trait Mode
  final case class Details(actual: Boolean) extends Mode
  private[sbt] case object DependencyTreeMode extends Mode { override def toString = "tree" }
  private[sbt] case object UsesMode extends Mode { override def toString = "inspect" }
  private[sbt] case object DefinitionsMode extends Mode { override def toString = "definitions" }
  val DependencyTree: Mode = DependencyTreeMode
  val Uses: Mode = UsesMode
  val Definitions: Mode = DefinitionsMode

  def parser: State => Parser[(Inspect.Mode, ScopedKey[_])] =
    (s: State) =>
      spacedModeParser(s) flatMap {
        case opt @ (UsesMode | DefinitionsMode) =>
          allKeyParser(s).map(key => (opt, Def.ScopedKey(Global, key)))
        case opt @ (DependencyTreeMode | Details(_)) => spacedKeyParser(s).map(key => (opt, key))
    }
  val spacedModeParser: (State => Parser[Mode]) = (s: State) => {
    val actual = "actual" ^^^ Details(true)
    val tree = "tree" ^^^ DependencyTree
    val uses = "uses" ^^^ Uses
    val definitions = "definitions" ^^^ Definitions
    token(Space ~> (tree | actual | uses | definitions)) ?? Details(false)
  }

  def allKeyParser(s: State): Parser[AttributeKey[_]] = {
    val keyMap = Project.structure(s).index.keyMap
    token(Space ~> (ID !!! "Expected key" examples keyMap.keySet)) flatMap { key =>
      Act.getKey(keyMap, key, idFun)
    }
  }
  val spacedKeyParser: State => Parser[ScopedKey[_]] = (s: State) =>
    Act.requireSession(s, token(Space) ~> Act.scopedKeyParser(s))

  def output(s: State, option: Mode, sk: Def.ScopedKey[_]): String = {
    val extracted = Project.extract(s)
    import extracted._
    option match {
      case Details(actual) =>
        Project.details(structure, actual, sk.scope, sk.key)
      case DependencyTreeMode =>
        val basedir = new File(Project.session(s).current.build)
        Project.settingGraph(structure, basedir, sk).dependsAscii(get(sbt.Keys.asciiGraphWidth))
      case UsesMode =>
        Project.showUses(Project.usedBy(structure, true, sk.key))
      case DefinitionsMode =>
        Project.showDefinitions(sk.key, Project.definitions(structure, true, sk.key))
    }
  }

}
