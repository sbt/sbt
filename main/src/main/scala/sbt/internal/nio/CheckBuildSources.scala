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
import sbt.nio.Keys._
import sbt.nio.file.{ ChangedFiles, Glob, RecursiveGlob }

private[sbt] object CheckBuildSources {
  private[sbt] def needReloadImpl: Def.Initialize[Task[StateTransform]] = Def.task {
    val logger = streams.value.log
    val st: State = state.value
    val firstTime = st.get(hasCheckedMetaBuild).fold(true)(_.compareAndSet(false, true))
    (onChangedBuildSource in Scope.Global).value match {
      case IgnoreSourceChanges => new StateTransform(st)
      case o =>
        logger.debug("Checking for meta build source updates")
        (changedInputFiles in checkBuildSources).value match {
          case Some(cf: ChangedFiles) if !firstTime =>
            val rawPrefix = s"build source files have changed\n" +
              (if (cf.created.nonEmpty) s"new files: ${cf.created.mkString("\n  ", "\n  ", "\n")}"
               else "") +
              (if (cf.deleted.nonEmpty)
                 s"deleted files: ${cf.deleted.mkString("\n  ", "\n  ", "\n")}"
               else "") +
              (if (cf.updated.nonEmpty)
                 s"updated files: ${cf.updated.mkString("\n  ", "\n  ", "\n")}"
               else "")
            val prefix = rawPrefix.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")
            if (o == ReloadOnSourceChanges) {
              logger.info(s"$prefix\nReloading sbt...")
              val remaining =
                Exec("reload", None, None) :: st.currentCommand.toList ::: st.remainingCommands
              new StateTransform(st.copy(currentCommand = None, remainingCommands = remaining))
            } else {
              val tail = "Apply these changes by running `reload`.\nAutomatically reload the " +
                "build when source changes are detected by setting " +
                "`Global / onChangedBuildSource := ReloadOnSourceChanges`.\nDisable this " +
                "warning by setting `Global / onChangedBuildSource := IgnoreSourceChanges`."
              logger.warn(s"$prefix\n$tail")
              new StateTransform(st)
            }
          case _ => new StateTransform(st)
        }
    }
  }
  private[sbt] def buildSourceFileInputs: Def.Initialize[Seq[Glob]] = Def.setting {
    if (onChangedBuildSource.value != IgnoreSourceChanges) {
      val baseDir = (LocalRootProject / baseDirectory).value
      val sourceFilter = "*.{sbt,scala,java}"
      val projectDir = baseDir / "project"
      Seq(
        Glob(baseDir, "*.sbt"),
        Glob(projectDir, sourceFilter),
        // We only want to recursively look in source because otherwise we have to search
        // the project target directories which is expensive.
        Glob(projectDir / "src", RecursiveGlob / sourceFilter),
      )
    } else Nil
  }
}
