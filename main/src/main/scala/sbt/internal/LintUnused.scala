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

object LintUnused {
  lazy val lintSettings: Seq[Setting[_]] = Seq(
    lintIncludeFilter := {
      val includes = includeLintKeys.value.map(_.scopedKey.key.label)
      keyName => includes(keyName)
    },
    lintExcludeFilter := {
      val excludes = excludeLintKeys.value.map(_.scopedKey.key.label)
      keyName => excludes(keyName) || keyName.startsWith("watch")
    },
    excludeLintKeys := Set(
      aggregate,
      concurrentRestrictions,
      commands,
      crossScalaVersions,
      initialize,
      lintUnusedKeysOnLoad,
      onLoad,
      onLoadMessage,
      onUnload,
      sbt.nio.Keys.watchTriggers,
    ),
    includeLintKeys := Set(
      scalacOptions,
      javacOptions,
      javaOptions,
      incOptions,
      compileOptions,
      packageOptions,
      mainClass,
      mappings,
      testOptions,
      classpathConfiguration,
      ivyConfiguration,
    ),
    Keys.lintUnused := lintUnusedTask.evaluated,
    Keys.lintUnusedKeysOnLoad := true,
  )

  // input task version of the lintUnused
  def lintUnusedTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val _ = Def.spaceDelimited().parsed // not used yet
    val state = Keys.state.value
    val log = streams.value.log
    val includeKeys = (lintIncludeFilter in Global).value
    val excludeKeys = (lintExcludeFilter in Global).value
    val result = lintUnused(state, includeKeys, excludeKeys)
    if (result.isEmpty) log.success("ok")
    else lintResultLines(result) foreach { log.warn(_) }
  }

  // function version of the lintUnused, based on just state
  def lintUnusedFunc(s: State): State = {
    val log = s.log
    val extracted = Project.extract(s)
    val includeKeys = extracted.get(lintIncludeFilter in Global)
    val excludeKeys = extracted.get(lintExcludeFilter in Global)
    if (extracted.get(lintUnusedKeysOnLoad in Global)) {
      val result = lintUnused(s, includeKeys, excludeKeys)
      lintResultLines(result) foreach { log.warn(_) }
    }
    s
  }

  def lintResultLines(
      result: Seq[(ScopedKey[_], String, Vector[SourcePosition])]
  ): Vector[String] = {
    import scala.collection.mutable.ListBuffer
    val buffer = ListBuffer.empty[String]

    if (result.isEmpty) Vector.empty
    else {
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
        "note: a setting might still be used by a command; to exclude a key from this `lintUnused` check"
      )
      buffer.append(
        "either append it to `Global / excludeLintKeys` or call .withRank(KeyRanks.Invisible) on the key"
      )
      buffer.toVector
    }
  }

  def lintUnused(
      state: State,
      includeKeys: String => Boolean,
      excludeKeys: String => Boolean
  ): Seq[(ScopedKey[_], String, Vector[SourcePosition])] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure
    val display = Def.showShortKey(None) // extracted.showKey
    val comp = structure.compiledMap
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
    val unusedKeys = withDefinedAts collect {
      case u
          if !isExcludeKey(u) && !isInvisible(u)
            && (isSettingKey(u) || isIncludeKey(u))
            && isLocallyDefined(u) =>
        u
    }
    (unusedKeys map { u =>
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
