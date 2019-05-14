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
  private[sbt] def needReloadImpl: Def.Initialize[Task[Unit]] = Def.task {
    val logger = streams.value.log
    val checkMetaBuildParam = state.value.get(hasCheckedMetaBuild)
    val firstTime = checkMetaBuildParam.fold(true)(_.get == false)
    onChangedBuildSource.value match {
      case IgnoreSourceChanges => ()
      case o =>
        logger.debug("Checking for meta build source updates")
        (changedInputFiles in checkBuildSources).value match {
          case Some(cf: ChangedFiles) if !firstTime =>
            val rawPrefix = s"Meta build source files have changed:\n" +
              (if (cf.created.nonEmpty) s"creations: ${cf.created.mkString("\n  ", "  \n", "\n")}"
               else "") +
              (if (cf.deleted.nonEmpty) s"deletions: ${cf.deleted.mkString("\n  ", "  \n", "\n")}"
               else "") +
              (if (cf.updated.nonEmpty) s"updates: ${cf.updated.mkString("\n  ", "  \n", "\n")}"
               else "")
            val prefix = rawPrefix.linesIterator.filterNot(_.trim.isEmpty).mkString("\n")
            if (o == ReloadOnSourceChanges) {
              logger.info(s"$prefix\nReloading sbt...")
              throw Reload
            } else {
              val tail = "Reload sbt with the 'reload' command to apply these changes. " +
                "To automatically reload upon meta build source changed detection, set " +
                "`Global / onChangedBuildSource := ReloadOnSourceChanges`. To disable this " +
                "warning, set `Global / onChangedBuildSource := IgnoreSourceChanges`"
              logger.warn(s"$prefix\n$tail")
            }
          case _ => ()
        }
    }
    checkMetaBuildParam.foreach(_.set(true))
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
