/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package coursierint

import lmcoursier.definitions.{
  Classifier as CClassifier,
  Configuration as CConfiguration,
  Extension as CExtension,
  Publication as CPublication,
  Type as CType
}
import sbt.librarymanagement.*
import sbt.Keys.*
import sbt.ProjectExtra.extract
import sbt.SlashSyntax0.*

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
      val projectPlatform = sbt.Keys.scalaModuleInfo.value.flatMap(_.platform)
      val ivyConfs = sbt.Keys.ivyConfigurations.value
      val extracted = Project.extract(s)
      import extracted.*

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

        // Platform suffix before cross suffix, matching the coordinate
        val base = projId.crossVersion match
          case _: Disabled => artifact.name
          case _           =>
            CrossVersion.addPlatformSuffix(artifact.name, projId.platformOpt, projectPlatform)
        val name = CrossVersion(projId.crossVersion, sv, sbv)
          .fold(base)(_(base))

        CPublication(
          name,
          CType(artifact.`type`),
          CExtension(artifact.extension),
          artifact.classifier.fold(CClassifier(""))(CClassifier(_))
        )
      }

      val sbtArtifactsPublication = sbtArtifacts.collect { case Some((config, artifact)) =>
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
        if (configs.isEmpty) ivyConfs.withFilter(_.isPublic).map(c => ConfigRef(c.name))
        else configs

      val extraSbtArtifactsPublication = for {
        artifact <- extraSbtArtifacts
        config <- allConfigsIfEmpty(artifact.configurations.map(x => ConfigRef(x.name)))
        // FIXME If some configurations from artifact.configurations are not public, they may leak here :\
      } yield CConfiguration(config.name) -> artifactPublication(artifact)

      sbtArtifactsPublication ++ extraSbtArtifactsPublication
    }
}
