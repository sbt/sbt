/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal.nio

import sbt.Keys.{ baseDirectory, state, streams }
import sbt.SlashSyntax0._
import sbt.io.syntax._
import sbt.nio.FileChanges
import sbt.nio.Keys._
import sbt.nio.file.{ Glob, ** }
import sbt.nio.file.syntax._

import scala.annotation.tailrec

private[sbt] object CheckBuildSources {
  private[sbt] def needReloadImpl: Def.Initialize[Task[StateTransform]] = Def.task {
    val logger = streams.value.log
    val st: State = state.value
    val firstTime = st.get(hasCheckedMetaBuild).fold(true)(_.compareAndSet(false, true))
    (onChangedBuildSource in Scope.Global).value match {
      case IgnoreSourceChanges => StateTransform(identity)
      case o =>
        import sbt.nio.FileStamp.Formats._
        logger.debug("Checking for meta build source updates")
        val previous = (inputFileStamps in checkBuildSources).previous
        val changes = (changedInputFiles in checkBuildSources).value
        previous.map(changes) match {
          case Some(fileChanges @ FileChanges(created, deleted, modified, _))
              if fileChanges.hasChanges && !firstTime =>
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
            if (o == ReloadOnSourceChanges) {
              logger.info(s"$prefix\nReloading sbt...")
              val remaining =
                Exec("reload", None, None) :: st.currentCommand.toList ::: st.remainingCommands
              StateTransform(_.copy(currentCommand = None, remainingCommands = remaining))
            } else {
              val tail = "Apply these changes by running `reload`.\nAutomatically reload the " +
                "build when source changes are detected by setting " +
                "`Global / onChangedBuildSource := ReloadOnSourceChanges`.\nDisable this " +
                "warning by setting `Global / onChangedBuildSource := IgnoreSourceChanges`."
              logger.warn(s"$prefix\n$tail")
              StateTransform(identity)
            }
          case _ => StateTransform(identity)
        }
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
