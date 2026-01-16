/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Keys.*
import sbt.internal.util.{ FilePosition, LinePosition, NoPosition, SourcePosition }
import java.io.File
import ProjectExtra.{ extract, scopedKeyData }
import Scope.Global
import sbt.Def.*

object LintUnused {
  lazy val lintSettings: Seq[Setting[?]] = Seq(
    lintIncludeFilter := {
      val includes = includeLintKeys.value.map(_.scopedKey.key.label)
      keyName => includes(keyName)
    },
    lintExcludeFilter := {
      val excludedPrefixes = List("release", "sonatype", "watch", "whitesource")
      val excludes = excludeLintKeys.value.map(_.scopedKey.key.label)
      keyName => excludes(keyName) || excludedPrefixes.exists(keyName.startsWith(_))
    },
    excludeLintKeys := Set(
      aggregate,
      concurrentRestrictions,
      commands,
      configuration,
      crossScalaVersions,
      crossSbtVersions,
      allowUnsafeScalaLibUpgrade,
      evictionWarningOptions,
      initialize,
      lintUnusedKeysOnLoad,
      localDigestCacheByteSize,
      onLoad,
      onLoadMessage,
      onUnload,
      pollInterval,
      sbt.nio.Keys.outputFileStamper,
      sbt.nio.Keys.watchTriggers,
      serverConnectionType,
      serverIdleTimeout,
      shellPrompt,
      sLog,
      traceLevel,
      sonaDeploymentName,
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
    val includeKeys = (Global / lintIncludeFilter).value
    val excludeKeys = (Global / lintExcludeFilter).value
    val result = lintUnused(state, includeKeys, excludeKeys)
    if (result.isEmpty) log.success("ok")
    else lintResultLines(result) foreach { log.warn(_) }
  }

  // function version of the lintUnused, based on just state
  def lintUnusedFunc(s: State): State = {
    val log = s.log
    val extracted = Project.extract(s)
    val includeKeys = extracted.get((Global / lintIncludeFilter))
    val excludeKeys = extracted.get((Global / lintExcludeFilter))
    if (extracted.get((Global / lintUnusedKeysOnLoad))) {
      val result = lintUnused(s, includeKeys, excludeKeys)
      lintResultLines(result) foreach { log.warn(_) }
    }
    s
  }

  def lintResultLines(
      result: Seq[(ScopedKey[?], String, Seq[SourcePosition])]
  ): Vector[String] = {
    import scala.collection.mutable.ListBuffer
    val buffer = ListBuffer.empty[String]

    if (result.isEmpty) Vector.empty
    else {
      val size = result.size
      if (size == 1) buffer.append("there's a key that's not used by any other settings/tasks:")
      else buffer.append(s"there are $size keys that are not used by any other settings/tasks:")
      buffer.append(" ")
      result foreach { case (_, str, positions) =>
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
  ): Seq[(ScopedKey[?], String, Seq[SourcePosition])] = {
    val extracted = Project.extract(state)
    val structure = extracted.structure
    val display = Def.showShortKey(None) // extracted.showKey
    val comp = structure.compiledMap
    val cMap = Def.flattenLocals(comp)
    val used: Set[ScopedKey[?]] = cMap.values.flatMap(_.dependencies).toSet
    val unused: Seq[ScopedKey[?]] = cMap.keys.filter(!used.contains(_)).toSeq
    val withDefinedAts: Seq[UnusedKey] = unused.map { u =>
      val data = Project.scopedKeyData(structure, u)
      val definedAt = comp.get(data.map(_.definingKey).getOrElse(u)) match
        case Some(c) => definedAtString(c.settings)
        case _       => Vector.empty
      UnusedKey(u, definedAt, data)
    }

    def isIncludeKey(u: UnusedKey): Boolean = includeKeys(u.scoped.key.label)
    def isExcludeKey(u: UnusedKey): Boolean = excludeKeys(u.scoped.key.label)
    def isSettingKey(u: UnusedKey): Boolean = u.data match {
      case Some(data) => data.settingValue.isDefined
      case _          => false
    }
    def isLocallyDefined(u: UnusedKey): Boolean = u.positions.exists {
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
    unusedKeys.map(u => (u.scoped, display.show(u.scoped), u.positions)).sortBy(_._2)
  }

  def lintScalaVersion(state: State): State = {
    val log = state.log
    val extracted = Project.extract(state)
    val structure = extracted.structure
    val comp = structure.compiledMap
    for
      p <- structure.allProjectRefs
      scope = Scope.Global.rescope(p)
      key = scalaVersion.rescope(scope)
      data = Project.scopedKeyData(structure, key.scopedKey)
      sv <- extracted.getOpt(key)
      isPlugin = extracted.get(sbtPlugin.rescope(scope))
      mb = extracted.get(isMetaBuild.rescope(scope))
      auto = extracted.get(autoScalaLibrary.rescope(scope))
      msi = extracted.get(managedScalaInstance.rescope(scope))
      (_, sk) = extracted.runTask(skip.rescope(scope.rescope(publish.key)), state)
      display = p match
        case ProjectRef(_, id) => id
        case _ | null          => Reference.display(p)
      c <- comp.get(data.map(_.definingKey).getOrElse(key.scopedKey))
      setting <- c.settings.headOption
    do
      if auto && msi && !isPlugin && !mb && !sk then
        setting.pos match
          case LinePosition(path, _) if path.endsWith("Defaults.scala") =>
            log.warn(
              s"""scalaVersion for subproject $display fell back to a default value $sv; declare it explicitly in build.sbt:
  scalaVersion := "$sv""""
            )
          case _ => ()
      else ()
    state
  }

  private case class UnusedKey(
      scoped: ScopedKey[?],
      positions: Seq[SourcePosition],
      data: Option[ScopedKeyData[?]]
  )

  private def definedAtString(settings: Seq[Setting[?]]): Seq[SourcePosition] = {
    settings flatMap { setting =>
      setting.pos match {
        case NoPosition => Vector.empty
        case pos        => Vector(pos)
      }
    }
  }
}
