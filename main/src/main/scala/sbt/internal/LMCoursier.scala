/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import coursier.core.{
  Attributes => CAttributes,
  Classifier,
  Configuration => CConfiguration,
  Dependency => CDependency,
  Extension => CExtension,
  Info => CInfo,
  Module,
  ModuleName,
  Organization => COrganization,
  Project => CProject,
  Publication => CPublication,
  Type => CType
}
import coursier.credentials.DirectCredentials
import coursier.lmcoursier._
import sbt.io.IO
import sbt.librarymanagement._
import Keys._
import sbt.librarymanagement.ivy.{
  FileCredentials,
  Credentials,
  DirectCredentials => IvyDirectCredentials
}
import sbt.ScopeFilter.Make._
import scala.collection.JavaConverters._

private[sbt] object LMCoursier {

  def coursierProjectTask: Def.Initialize[sbt.Task[CProject]] =
    Def.task {
      val auOpt = apiURL.value
      val proj = Inputs.coursierProject(
        projectID.value,
        allDependencies.value,
        excludeDependencies.value,
        ivyConfigurations.value,
        scalaVersion.value,
        scalaBinaryVersion.value,
        streams.value.log
      )
      auOpt match {
        case Some(au) =>
          val props = proj.properties :+ ("info.apiURL" -> au.toString)
          proj.copy(properties = props)
        case _ => proj
      }
    }

  def coursierConfigurationTask(
      withClassifiers: Boolean,
      sbtClassifiers: Boolean
  ): Def.Initialize[Task[CoursierConfiguration]] =
    Def.taskDyn {
      val s = streams.value
      val log = s.log
      val resolversTask =
        if (sbtClassifiers)
          csrSbtResolvers
        else
          csrRecursiveResolvers
      val classifiersTask: sbt.Def.Initialize[sbt.Task[Option[Seq[Classifier]]]] =
        if (withClassifiers && !sbtClassifiers)
          Def.task(Some(sbt.Keys.transitiveClassifiers.value.map(Classifier(_))))
        else
          Def.task(None)
      Def.task {
        val rs = resolversTask.value
        val scalaOrg = scalaOrganization.value
        val scalaVer = scalaVersion.value
        val interProjectDependencies = csrInterProjectDependencies.value
        val excludeDeps = Inputs.exclusions(
          excludeDependencies.value,
          scalaVer,
          scalaBinaryVersion.value,
          streams.value.log
        )
        val fallbackDeps = csrFallbackDependencies.value
        val autoScalaLib = autoScalaLibrary.value && scalaModuleInfo.value.forall(
          _.overrideScalaVersion
        )
        val profiles = csrMavenProfiles.value
        val credentials = credentialsTask.value

        val createLogger = csrLogger.value

        val cache = csrCachePath.value

        val internalSbtScalaProvider = appConfiguration.value.provider.scalaProvider
        val sbtBootJars = internalSbtScalaProvider.jars()
        val sbtScalaVersion = internalSbtScalaProvider.version()
        val sbtScalaOrganization = "org.scala-lang" // always assuming sbt uses mainline scala
        val classifiers = classifiersTask.value
        Classpaths.warnResolversConflict(rs, log)
        CoursierConfiguration()
          .withResolvers(rs.toVector)
          .withInterProjectDependencies(interProjectDependencies.toVector)
          .withFallbackDependencies(fallbackDeps.toVector)
          .withExcludeDependencies(
            excludeDeps.toVector.sorted
              .map {
                case (o, n) =>
                  (o.value, n.value)
              }
          )
          .withAutoScalaLibrary(autoScalaLib)
          .withSbtScalaJars(sbtBootJars.toVector)
          .withSbtScalaVersion(sbtScalaVersion)
          .withSbtScalaOrganization(sbtScalaOrganization)
          .withClassifiers(classifiers.toVector.flatten.map(_.value))
          .withHasClassifiers(classifiers.nonEmpty)
          .withMavenProfiles(profiles.toVector.sorted)
          .withScalaOrganization(scalaOrg)
          .withScalaVersion(scalaVer)
          .withCredentials(credentials)
          .withLogger(createLogger)
          .withCache(cache)
          .withLog(log)
      }
    }

  val credentialsTask = Def.task {
    val log = streams.value.log

    val creds = sbt.Keys.credentials.value
      .flatMap {
        case dc: IvyDirectCredentials => List(dc)
        case fc: FileCredentials =>
          Credentials.loadCredentials(fc.path) match {
            case Left(err) =>
              log.warn(s"$err, ignoring it")
              Nil
            case Right(dc) => List(dc)
          }
      }
      .map { c =>
        DirectCredentials()
          .withHost(c.host)
          .withUsername(c.userName)
          .withPassword(c.passwd)
          .withRealm(Some(c.realm).filter(_.nonEmpty))
      }
    creds ++ csrExtraCredentials.value
  }

  def coursierRecursiveResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] =
    Def.taskDyn {
      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projects = allRecursiveInterDependencies(state, projectRef)
      Def.task {
        csrResolvers.all(ScopeFilter(inProjects(projectRef +: projects: _*))).value.flatten
      }
    }

  def coursierResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] =
    Def.taskDyn {
      val bootResOpt = bootResolvers.value
      val overrideFlag = overrideBuildResolvers.value
      Def.task {
        val result0 = resultTask(bootResOpt, overrideFlag).value
        val reorderResolvers = true // coursierReorderResolvers.value
        val keepPreloaded = false // coursierKeepPreloaded.value
        val paths = ivyPaths.value
        val result1 =
          if (reorderResolvers) ResolutionParams.reorderResolvers(result0)
          else result0
        val result2 =
          paths.ivyHome match {
            case Some(ivyHome) =>
              val ivyHomeUri = IO.toURI(ivyHome).getSchemeSpecificPart
              result1 map {
                case r: FileRepository =>
                  val ivyPatterns = r.patterns.ivyPatterns map {
                    _.replaceAllLiterally("$" + "{ivy.home}", ivyHomeUri)
                  }
                  val artifactPatterns = r.patterns.artifactPatterns map {
                    _.replaceAllLiterally("$" + "{ivy.home}", ivyHomeUri)
                  }
                  val p =
                    r.patterns.withIvyPatterns(ivyPatterns).withArtifactPatterns(artifactPatterns)
                  r.withPatterns(p)
                case r => r
              }
            case _ => result1
          }
        if (keepPreloaded) result2
        else
          result2.filter { r =>
            !r.name.startsWith("local-preloaded")
          }
      }
    }

  private val pluginIvySnapshotsBase = Resolver.SbtRepositoryRoot.stripSuffix("/") + "/ivy-snapshots"

  def coursierSbtResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] = Def.task {
    val resolvers =
      sbt.Classpaths
        .bootRepositories(appConfiguration.value)
        .toSeq
        .flatten ++ // required because of the hack above it seems
        externalResolvers.in(updateSbtClassifiers).value

    val pluginIvySnapshotsFound = resolvers.exists {
      case repo: URLRepository =>
        repo.patterns.artifactPatterns.headOption
          .exists(_.startsWith(pluginIvySnapshotsBase))
      case _ => false
    }

    val resolvers0 =
      if (pluginIvySnapshotsFound && !resolvers.contains(Classpaths.sbtPluginReleases))
        resolvers :+ Classpaths.sbtPluginReleases
      else
        resolvers
    val keepPreloaded = true // coursierKeepPreloaded.value
    if (keepPreloaded)
      resolvers0
    else
      resolvers0.filter { r =>
        !r.name.startsWith("local-preloaded")
      }
  }

  def coursierInterProjectDependenciesTask: Def.Initialize[sbt.Task[Seq[CProject]]] =
    Def.taskDyn {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projectRefs = allRecursiveInterDependencies(state, projectRef)

      Def.task {
        val projects = csrProject.all(ScopeFilter(inProjects(projectRefs: _*))).value
        val projectModules = projects.map(_.module).toSet

        // this includes org.scala-sbt:global-plugins referenced from meta-builds in particular
        val extraProjects = sbt.Keys.projectDescriptors.value
          .map {
            case (k, v) =>
              moduleFromIvy(k) -> v
          }
          .filter {
            case (module, _) =>
              !projectModules(module)
          }
          .toVector
          .map {
            case (module, v) =>
              val configurations = v.getConfigurations.map { c =>
                CConfiguration(c.getName) -> c.getExtends.map(CConfiguration(_)).toSeq
              }.toMap
              val deps = v.getDependencies.flatMap(dependencyFromIvy)
              CProject(
                module,
                v.getModuleRevisionId.getRevision,
                deps,
                configurations,
                None,
                Nil,
                Nil,
                Nil,
                None,
                None,
                None,
                relocated = false,
                None,
                Nil,
                CInfo.empty
              )
          }

        projects ++ extraProjects
      }
    }

  def coursierFallbackDependenciesTask: Def.Initialize[sbt.Task[Seq[FallbackDependency]]] =
    Def.taskDyn {
      val s = state.value
      val projectRef = sbt.Keys.thisProjectRef.value

      val projects = allRecursiveInterDependencies(s, projectRef)
      Def.task {
        val allDeps =
          allDependencies.all(ScopeFilter(inProjects(projectRef +: projects: _*))).value.flatten

        FromSbt.fallbackDependencies(
          allDeps,
          scalaVersion.in(projectRef).value,
          scalaBinaryVersion.in(projectRef).value
        )
      }
    }

  def publicationsSetting(packageConfigs: Seq[(Configuration, CConfiguration)]): Def.Setting[_] = {
    csrPublications := coursierPublicationsTask(packageConfigs: _*).value
  }

  def coursierPublicationsTask(
      configsMap: (Configuration, CConfiguration)*
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
            publishArtifact
              .in(projectRef)
              .in(packageBin)
              .in(config)
          ).getOrElse(false)

          if (publish)
            getOpt(
              artifact
                .in(projectRef)
                .in(packageBin)
                .in(config)
            ).map(targetConfig -> _)
          else
            None
        }

      val sbtSourceArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish = getOpt(
            publishArtifact
              .in(projectRef)
              .in(packageSrc)
              .in(config)
          ).getOrElse(false)

          if (publish)
            getOpt(
              artifact
                .in(projectRef)
                .in(packageSrc)
                .in(config)
            ).map(sourcesConfigOpt.getOrElse(targetConfig) -> _)
          else
            None
        }

      val sbtDocArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish =
            getOpt(
              publishArtifact
                .in(projectRef)
                .in(packageDoc)
                .in(config)
            ).getOrElse(false)

          if (publish)
            getOpt(
              artifact
                .in(projectRef)
                .in(packageDoc)
                .in(config)
            ).map(docsConfigOpt.getOrElse(targetConfig) -> _)
          else
            None
        }

      val sbtArtifacts = sbtBinArtifacts ++ sbtSourceArtifacts ++ sbtDocArtifacts

      def artifactPublication(artifact: Artifact) = {

        val name = FromSbt.sbtCrossVersionName(
          artifact.name,
          projId.crossVersion,
          sv,
          sbv
        )

        CPublication(
          name,
          CType(artifact.`type`),
          CExtension(artifact.extension),
          artifact.classifier.fold(Classifier.empty)(Classifier(_))
        )
      }

      val sbtArtifactsPublication = sbtArtifacts.collect {
        case Some((config, artifact)) =>
          config -> artifactPublication(artifact)
      }

      val stdArtifactsSet = sbtArtifacts.flatMap(_.map { case (_, a) => a }.toSeq).toSet

      // Second-way of getting artifacts from SBT
      // No obvious way of getting the corresponding  publishArtifact  value for the ones
      // only here, it seems.
      val extraSbtArtifacts = getOpt(
        sbt.Keys.artifacts
          .in(projectRef)
      ).getOrElse(Nil)
        .filterNot(stdArtifactsSet)

      // Seems that SBT does that - if an artifact has no configs,
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

  private def moduleFromIvy(id: org.apache.ivy.core.module.id.ModuleRevisionId): Module =
    Module(
      COrganization(id.getOrganisation),
      ModuleName(id.getName),
      id.getExtraAttributes.asScala.map {
        case (k0, v0) => k0.asInstanceOf[String] -> v0.asInstanceOf[String]
      }.toMap
    )

  private def dependencyFromIvy(
      desc: org.apache.ivy.core.module.descriptor.DependencyDescriptor
  ): Seq[(CConfiguration, CDependency)] = {

    val id = desc.getDependencyRevisionId
    val module = moduleFromIvy(id)
    val exclusions = desc.getAllExcludeRules.map { rule =>
      // we're ignoring rule.getConfigurations and rule.getMatcher here
      val modId = rule.getId.getModuleId
      // we're ignoring modId.getAttributes here
      (COrganization(modId.getOrganisation), ModuleName(modId.getName))
    }.toSet

    val configurations = desc.getModuleConfigurations.toVector
      .flatMap(s => coursier.ivy.IvyXml.mappings(s))

    def dependency(conf: CConfiguration, attr: CAttributes) = CDependency(
      module,
      id.getRevision,
      conf,
      exclusions,
      attr,
      optional = false,
      desc.isTransitive
    )

    val attributes: CConfiguration => CAttributes = {

      val artifacts = desc.getAllDependencyArtifacts

      val m = artifacts.toVector.flatMap { art =>
        val attr = CAttributes(CType(art.getType), Classifier.empty)
        art.getConfigurations.map(CConfiguration(_)).toVector.map { conf =>
          conf -> attr
        }
      }.toMap

      c => m.getOrElse(c, CAttributes.empty)
    }

    configurations.map {
      case (from, to) =>
        from -> dependency(to, attributes(to))
    }
  }

  private def resultTask(
      bootResOpt: Option[Seq[Resolver]],
      overrideFlag: Boolean
  ): Def.Initialize[sbt.Task[Seq[Resolver]]] =
    bootResOpt.filter(_ => overrideFlag) match {
      case Some(r) => Def.task(r)
      case None =>
        Def.taskDyn {
          val extRes = externalResolvers.value
          val isSbtPlugin = sbtPlugin.value
          if (isSbtPlugin)
            Def.task {
              Seq(
                sbtResolver.value,
                Classpaths.sbtPluginReleases
              ) ++ extRes
            } else
            Def.task(extRes)
        }
    }

  def allRecursiveInterDependencies(state: sbt.State, projectRef: sbt.ProjectRef) = {
    def dependencies(map: Map[String, Seq[String]], id: String): Set[String] = {

      def helper(map: Map[String, Seq[String]], acc: Set[String]): Set[String] =
        if (acc.exists(map.contains)) {
          val (kept, rem) = map.partition { case (k, _) => acc(k) }
          helper(rem, acc ++ kept.valuesIterator.flatten)
        } else
          acc

      helper(map - id, map.getOrElse(id, Nil).toSet)
    }

    val allProjectsDeps =
      for (p <- Project.structure(state).allProjects)
        yield p.id -> p.dependencies.map(_.project.project)

    val deps = dependencies(allProjectsDeps.toMap, projectRef.project)

    Project.structure(state).allProjectRefs.filter(p => deps(p.project))
  }
}
