/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import lmcoursier.definitions.{
  Classifier => CClassifier,
  Configuration => CConfiguration,
  Extension => CExtension,
  Publication => CPublication,
  Type => CType
}
import sbt.librarymanagement._
import sbt.Keys._
import sbt.SlashSyntax0._

object CoursierArtifactsTasks {
  def coursierPublicationsTask(
      configsMap: (sbt.librarymanagement.Configuration, CConfiguration)*
  ): Def.Initialize[sbt.Task[Seq[(CConfiguration, CPublication)]]] =
    Def.task {
      val s = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value
      val projId = sbt.Keys.projectID.value
      val sv = sbt.Keys.scalaVersion.value
      val sbv = sbt.Keys.scalaBinaryVersion.value
      val ivyConfs = sbt.Keys.ivyConfigurations.value
      val extracted = Project.extract(s)
      import extracted._

      val sourcesConfigOpt =
        if (ivyConfigurations.value.exists(_.name == "sources"))
          Some(CConfiguration("sources"))
        else
          None

      val docsConfigOpt =
        if (ivyConfigurations.value.exists(_.name == "docs"))
          Some(CConfiguration("docs"))
        else
          None

      val sbtBinArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish = getOpt(
            projectRef / config / packageBin / publishArtifact
          ).getOrElse(false)

          if (publish)
            getOpt(
              projectRef / config / packageBin / artifact
            ).map(targetConfig -> _)
          else
            None
        }

      val sbtSourceArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish = getOpt(
            projectRef / config / packageSrc / publishArtifact
          ).getOrElse(false)

          if (publish)
            getOpt(
              projectRef / config / packageSrc / artifact
            ).map(sourcesConfigOpt.getOrElse(targetConfig) -> _)
          else
            None
        }

      val sbtDocArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish =
            getOpt(
              projectRef / config / packageDoc / publishArtifact
            ).getOrElse(false)

          if (publish)
            getOpt(
              projectRef / config / packageDoc / artifact
            ).map(docsConfigOpt.getOrElse(targetConfig) -> _)
          else
            None
        }

      val sbtArtifacts = sbtBinArtifacts ++ sbtSourceArtifacts ++ sbtDocArtifacts

      def artifactPublication(artifact: Artifact) = {

        val name = CrossVersion(projId.crossVersion, sv, sbv)
          .fold(artifact.name)(_(artifact.name))

        CPublication(
          name,
          CType(artifact.`type`),
          CExtension(artifact.extension),
          artifact.classifier.fold(CClassifier(""))(CClassifier(_))
        )
      }

      val sbtArtifactsPublication = sbtArtifacts.collect {
        case Some((config, artifact)) =>
          config -> artifactPublication(artifact)
      }

      val stdArtifactsSet = sbtArtifacts.flatMap(_.map { case (_, a) => a }.toSeq).toSet

      // Second-way of getting artifacts from sbt
      // No obvious way of getting the corresponding  publishArtifact  value for the ones
      // only here, it seems.
      val extraSbtArtifacts = getOpt(
        projectRef / sbt.Keys.artifacts
      ).getOrElse(Nil)
        .filterNot(stdArtifactsSet)

      // Seems that sbt does that - if an artifact has no configs,
      // it puts it in all of them. See for example what happens to
      // the standalone JAR artifact of the coursier cli module.
      def allConfigsIfEmpty(configs: Iterable[ConfigRef]): Iterable[ConfigRef] =
        if (configs.isEmpty) ivyConfs.filter(_.isPublic).map(c => ConfigRef(c.name)) else configs

      val extraSbtArtifactsPublication = for {
        artifact <- extraSbtArtifacts
        config <- allConfigsIfEmpty(artifact.configurations.map(x => ConfigRef(x.name)))
        // FIXME If some configurations from artifact.configurations are not public, they may leak here :\
      } yield CConfiguration(config.name) -> artifactPublication(artifact)

      sbtArtifactsPublication ++ extraSbtArtifactsPublication
    }
}
