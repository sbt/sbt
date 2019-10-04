/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Keys._
import Def.{ Setting, ScopedKey }
import sbt.internal.util.{ FilePosition, NoPosition, SourcePosition }
import java.io.File
import Scope.Global
import sbt.Def._

object LintBuild {
  lazy val lintSettings: Seq[Setting[_]] = Seq(
    excludeLintKeys := Set(
      aggregate,
      concurrentRestrictions,
      commands,
      crossScalaVersions,
      onLoadMessage,
      sbt.nio.Keys.watchTriggers,
    ),
    includeLintKeys := Set(
      scalacOptions,
      javacOptions,
      javaOptions,
      incOptions,
      compileOptions,
      packageOptions,
      mappings,
      testOptions,
    ),
    lintBuild := lintBuildTask.evaluated,
  )

  def lintBuildTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val _ = Def.spaceDelimited().parsed // not used yet
    val state = Keys.state.value
    val log = streams.value.log
    val includeKeys = (includeLintKeys in Global).value map { _.scopedKey.key.label }
    val excludeKeys = (excludeLintKeys in Global).value map { _.scopedKey.key.label }
    val result = lint(state, includeKeys, excludeKeys)
    if (result.isEmpty) log.success("ok")
    else lintResultLines(result) foreach { log.warn(_) }
  }

  def lintResultLines(
      result: Seq[(ScopedKey[_], String, Vector[SourcePosition])]
  ): Vector[String] = {
    import scala.collection.mutable.ListBuffer
    val buffer = ListBuffer.empty[String]

    val size = result.size
    if (size == 1) buffer.append("there's a key that's not used by any other settings/tasks:")
    else buffer.append(s"there are $size keys that are not used by any other settings/tasks:")
    buffer.append(" ")
    result foreach {
      case (_, str, positions) =>
        buffer.append(s"* $str")
        positions foreach {
          case pos: FilePosition => buffer.append(s"  +- ${pos.path}:${pos.startLine}")
          case _                 => ()
        }
    }
    buffer.append(" ")
    buffer.append(
      "note: a setting might still be used by a command; to exclude a key from this `lintBuild` check"
    )
    buffer.append(
      "either append it to `Global / excludeLintKeys` or set call .withRank(KeyRanks.Invisible) on the key"
    )
    buffer.toVector
  }

  def lint(
      state: State,
      includeKeys: Set[String],
      excludeKeys: Set[String]
  ): Seq[(ScopedKey[_], String, Vector[SourcePosition])] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure
    val display = Def.showShortKey(None) // extracted.showKey
    val actual = true
    val comp =
      Def.compiled(structure.settings, actual)(structure.delegates, structure.scopeLocal, display)
    val cMap = Def.flattenLocals(comp)
    val used: Set[ScopedKey[_]] = cMap.values.flatMap(_.dependencies).toSet
    val unused: Seq[ScopedKey[_]] = cMap.keys.filter(!used.contains(_)).toSeq
    val withDefinedAts: Seq[UnusedKey] = unused map { u =>
      val definingScope = structure.data.definingScope(u.scope, u.key)
      val definingScoped = definingScope match {
        case Some(sc) => ScopedKey(sc, u.key)
        case _        => u
      }
      val definedAt = comp.get(definingScoped) match {
        case Some(c) => definedAtString(c.settings.toVector)
        case _       => Vector.empty
      }
      val data = Project.scopedKeyData(structure, u.scope, u.key)
      UnusedKey(u, definedAt, data)
    }

    def isIncludeKey(u: UnusedKey): Boolean = includeKeys(u.scoped.key.label)
    def isExcludeKey(u: UnusedKey): Boolean = excludeKeys(u.scoped.key.label)
    def isSettingKey(u: UnusedKey): Boolean = u.data match {
      case Some(data) => data.settingValue.isDefined
      case _          => false
    }
    def isLocallyDefined(u: UnusedKey): Boolean = u.positions exists {
      case pos: FilePosition => pos.path.contains(File.separator)
      case _                 => false
    }
    def isInvisible(u: UnusedKey): Boolean = u.scoped.key.rank == KeyRanks.Invisible
    val unusedSettingKeys = withDefinedAts collect {
      case u
          if !isExcludeKey(u) && !isInvisible(u)
            && (isSettingKey(u) || isIncludeKey(u))
            && isLocallyDefined(u) =>
        u
    }
    (unusedSettingKeys map { u =>
      (u.scoped, display.show(u.scoped), u.positions)
    }).sortBy(_._2)
  }

  private[this] case class UnusedKey(
      scoped: ScopedKey[_],
      positions: Vector[SourcePosition],
      data: Option[ScopedKeyData[_]]
  )

  private def definedAtString(settings: Vector[Setting[_]]): Vector[SourcePosition] = {
    settings flatMap { setting =>
      setting.pos match {
        case NoPosition => Vector.empty
        case pos        => Vector(pos)
      }
    }
  }
}
