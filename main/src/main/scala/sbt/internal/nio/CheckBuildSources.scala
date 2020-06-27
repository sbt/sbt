/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal.nio

import java.nio.file.Path
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicReference }
import sbt.BasicCommandStrings.{ RebootCommand, Shutdown, TerminateAction }
import sbt.Keys.{ baseDirectory, pollInterval, state }
import sbt.Scope.Global
import sbt.SlashSyntax0._
import sbt.internal.CommandStrings.LoadProject
import sbt.internal.SysProp
import sbt.internal.util.AttributeKey
import sbt.io.syntax._
import sbt.nio.FileChanges
import sbt.nio.FileStamp
import sbt.nio.Keys._
import sbt.nio.file.{ FileAttributes, FileTreeView, Glob, ** }
import sbt.nio.file.syntax._
import sbt.nio.Settings

import scala.annotation.tailrec
import scala.concurrent.duration.{ Deadline => SDeadline, _ }

/**
 * This class is used to determine whether sbt needs to automatically reload
 * the build because its source files have changed. In general, it will use
 * a FileTreeRepository to monitor the build source directories and it will
 * only actually check whether any sources have changed if the monitor has
 * detected any events. Because it's using asynchronous monitoring by default,
 * the automatic reloading should not be relied upon in batch scripting. It is
 * possible to configure this feature by setting
 * `Global / onChangedBuildSource / pollInterval`. When this value is set to
 * 0.seconds, then it will poll every time. Otherwise, it will only repoll
 * the build files if the poll interval has elapsed.
 */
private[sbt] class CheckBuildSources extends AutoCloseable {
  private[this] val repository = new AtomicReference[FileTreeRepository[FileAttributes]]
  private[this] val pollingPeriod = new AtomicReference[FiniteDuration]
  private[this] val sources = new AtomicReference[Seq[Glob]](Nil)
  private[this] val needUpdate = new AtomicBoolean(true)
  private[this] val lastPolled = new AtomicReference[SDeadline](SDeadline.now)
  private[this] val previousStamps = new AtomicReference[Seq[(Path, FileStamp)]]
  private[sbt] def fileTreeRepository: Option[FileTreeRepository[FileAttributes]] =
    Option(repository.get)
  private def getStamps(force: Boolean) = {
    val now = SDeadline.now
    val lp = lastPolled.getAndSet(now)
    if (force || lp + pollingPeriod.get <= now) {
      FileTreeView.default.list(sources.get) flatMap {
        case (p, a) if a.isRegularFile => FileStamp.hash(p).map(p -> _)
        case _                         => None
      }
    } else previousStamps.get
  }
  private def reset(state: State): Unit = {
    val extracted = Project.extract(state)
    val interval = extracted.get(checkBuildSources / pollInterval)
    val newSources = extracted.get(Global / checkBuildSources / fileInputs)
    if (interval >= 0.seconds || "polling" == SysProp.watchMode) {
      Option(repository.getAndSet(null)).foreach(_.close())
      pollingPeriod.set(interval)
    } else {
      pollingPeriod.set(0.seconds)
      repository.get match {
        case null =>
          val repo = FileTreeRepository.default
          repo.addObserver(_ => needUpdate.set(true))
          repository.set(repo)
          newSources.foreach(g => repo.register(g))
        case r =>
      }
    }
    val previousSources = sources.getAndSet(newSources)
    if (previousSources != newSources) {
      fileTreeRepository.foreach(r => newSources.foreach(g => r.register(g)))
      previousStamps.set(getStamps(force = true))
    }
  }
  private def needCheck(state: State, cmd: String): Boolean = {
    val allCmds = state.remainingCommands
      .map(_.commandLine)
      .dropWhile(!_.startsWith(BasicCommandStrings.MapExec)) :+ cmd
    val commands =
      allCmds.flatMap(_.split(";").flatMap(_.trim.split(" ").headOption).filterNot(_.isEmpty))
    val filter = (c: String) =>
      c == LoadProject || c == RebootCommand || c == TerminateAction || c == Shutdown
    val res = !commands.exists(filter)
    if (!res) {
      previousStamps.set(getStamps(force = true))
      needUpdate.set(false)
    }
    res
  }
  @inline private def forceCheck = fileTreeRepository.isEmpty
  private[sbt] def needsReload(state: State, cmd: String) = {
    (needCheck(state, cmd) && (forceCheck || needUpdate.compareAndSet(true, false))) && {
      val extracted = Project.extract(state)
      val onChanges = extracted.get(Global / onChangedBuildSource)
      val logger = state.globalLogging.full
      val current = getStamps(force = false)
      val previous = previousStamps.getAndSet(current)
      Settings.changedFiles(previous, current) match {
        case fileChanges @ FileChanges(created, deleted, modified, _) if fileChanges.hasChanges =>
          val rawPrefix = s"build source files have changed\n" +
            (if (created.nonEmpty) s"new files: ${created.mkString("\n  ", "\n  ", "\n")}"
             else "") +
            (if (deleted.nonEmpty)
               s"deleted files: ${deleted.mkString("\n  ", "\n  ", "\n")}"
             else "") +
            (if (modified.nonEmpty)
               s"modified files: ${modified.mkString("\n  ", "\n  ", "\n")}"
             else "")
          val prefix = rawPrefix.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")
          if (onChanges == ReloadOnSourceChanges) {
            logger.info(s"$prefix\nReloading sbt...")
            true
          } else {
            val tail = "Apply these changes by running `reload`.\nAutomatically reload the " +
              "build when source changes are detected by setting " +
              "`Global / onChangedBuildSource := ReloadOnSourceChanges`.\nDisable this " +
              "warning by setting `Global / onChangedBuildSource := IgnoreSourceChanges`."
            logger.warn(s"$prefix\n$tail")
            false
          }
        case _ => false
      }
    }
  }
  override def close(): Unit = {}
}

private[sbt] object CheckBuildSources {
  private[sbt] val CheckBuildSourcesKey =
    AttributeKey[CheckBuildSources]("check-build-source", "", KeyRanks.Invisible)
  /*
   * Reuse the same instance of CheckBuildSources across reloads but reset the state. This
   * should allow the `set` command to work with checkBuildSources / fileInputs and
   * checkBuildSources / pollInterval. The latter makes it possible to switch between
   * the asynchronous and polling implementations during the same sbt session.
   */
  private[sbt] def init(state: State): State = state.get(CheckBuildSourcesKey) match {
    case Some(cbs) =>
      cbs.reset(state)
      state
    case _ =>
      val cbs = new CheckBuildSources
      cbs.reset(state)
      state.put(CheckBuildSourcesKey, cbs)
  }
  private[sbt] def needReloadImpl: Def.Initialize[Task[StateTransform]] = Def.task {
    val st = state.value
    st.get(CheckBuildSourcesKey) match {
      case Some(cbs) if (cbs.needsReload(st, "")) => StateTransform("reload" :: (_: State))
      case _                                      => StateTransform(identity)
    }
  }
  private[sbt] def buildSourceFileInputs: Def.Initialize[Seq[Glob]] = Def.setting {
    if (onChangedBuildSource.value != IgnoreSourceChanges) {
      val baseDir = (LocalRootProject / baseDirectory).value
      val projectDir = baseDir / "project"
      @tailrec
      def projectGlobs(projectDir: File, globs: Seq[Glob]): Seq[Glob] = {
        val glob = projectDir.toGlob
        val updatedGlobs = globs ++ Seq(
          glob / "*.{sbt,scala,java}",
          // We only want to recursively look in source because otherwise we have to search
          // the project target directories which is expensive.
          glob / "src" / ** / "*.{scala,java}"
        )
        val nextLevel = projectDir / "project"
        if (nextLevel.exists) projectGlobs(nextLevel, updatedGlobs) else updatedGlobs
      }
      projectGlobs(projectDir, baseDir.toGlob / "*.sbt" :: Nil)
    } else Nil
  }
}
