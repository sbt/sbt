package coursier.sbtcoursier

import coursier.ProjectCache
import coursier.cache.FileCache
import coursier.core._
import coursier.internal.Typelevel
import coursier.lmcoursier._
import coursier.sbtcoursier.Keys._
import coursier.sbtcoursiershared.InputsTasks.credentialsTask
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport._
import sbt.Def
import sbt.Keys._

object ResolutionTasks {

  def resolutionsTask(
    sbtClassifiers: Boolean = false
  ): Def.Initialize[sbt.Task[Map[Set[Configuration], coursier.Resolution]]] = {

    val currentProjectTask: sbt.Def.Initialize[sbt.Task[(Project, Seq[FallbackDependency], Seq[Set[Configuration]])]] =
      if (sbtClassifiers)
        Def.task {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value
          val cm = coursierSbtClassifiersModule.value
          val proj = FromSbt.sbtClassifiersProject(cm, sv, sbv)

          val fallbackDeps = FromSbt.fallbackDependencies(
            cm.dependencies,
            sv,
            sbv
          )

          (proj, fallbackDeps, Vector(cm.configurations.map(c => Configuration(c.name)).toSet))
        }
      else
        Def.task {
          val baseConfigGraphs = coursierConfigGraphs.value
          (coursierProject.value.copy(publications = coursierPublications.value), coursierFallbackDependencies.value, baseConfigGraphs)
        }

    val resolversTask =
      if (sbtClassifiers)
        Def.task(coursierSbtResolvers.value)
      else
        Def.task(coursierRecursiveResolvers.value.distinct)

    Def.task {
      val projectName = thisProjectRef.value.project

      val sv = scalaVersion.value
      val sbv = scalaBinaryVersion.value

      val interProjectDependencies = coursierInterProjectDependencies.value

      val parallelDownloads = coursierParallelDownloads.value
      val checksums = coursierChecksums.value
      val maxIterations = coursierMaxIterations.value
      val cachePolicies = coursierCachePolicies.value
      val ttl = coursierTtl.value
      val cache = coursierCache.value
      val createLogger = coursierLogger.value

      val log = streams.value.log

      // are these always defined? (e.g. for Java only projects?)
      val so = Organization(scalaOrganization.value)

      val userForceVersions = dependencyOverrides
        .value
        .map(FromSbt.moduleVersion(_, sv, sbv))
        .toMap

      val verbosityLevel = coursierVerbosity.value

      val userEnabledProfiles = mavenProfiles.value

      val typelevel = Organization(scalaOrganization.value) == Typelevel.typelevelOrg

      val interProjectRepo = InterProjectRepository(interProjectDependencies)

      val ivyProperties = ResolutionParams.defaultIvyProperties()

      val authenticationByRepositoryId = coursierCredentials.value.mapValues(_.authentication)

      val (currentProject, fallbackDependencies, configGraphs) = currentProjectTask.value

      val autoScalaLib = autoScalaLibrary.value && scalaModuleInfo.value.forall(_.overrideScalaVersion)

      val resolvers = resolversTask.value

      // TODO Warn about possible duplicated modules from source repositories?

      val credentials = credentialsTask.value

      val parentProjectCache: ProjectCache = coursierParentProjectCache.value
        .get(resolvers)
        .map(_.foldLeft[ProjectCache](Map.empty)(_ ++ _))
        .getOrElse(Map.empty)

      val mainRepositories = resolvers
        .flatMap { resolver =>
          FromSbt.repository(
            resolver,
            ivyProperties,
            log,
            authenticationByRepositoryId.get(resolver.name)
          )
        }

      val resOrError = ResolutionRun.resolutions(
        ResolutionParams(
          dependencies = currentProject.dependencies,
          fallbackDependencies = fallbackDependencies,
          configGraphs = configGraphs,
          autoScalaLibOpt = if (autoScalaLib) Some((so, sv)) else None,
          mainRepositories = mainRepositories,
          parentProjectCache = parentProjectCache,
          interProjectDependencies = interProjectDependencies,
          internalRepositories = Seq(interProjectRepo),
          sbtClassifiers = sbtClassifiers,
          projectName = projectName,
          loggerOpt = createLogger,
          cache = FileCache()
            .withLocation(cache)
            .withCachePolicies(cachePolicies)
            .withTtl(ttl)
            .withChecksums(checksums)
            .withCredentials(credentials),
          parallel = parallelDownloads,
          params = coursier.params.ResolutionParams()
            .withMaxIterations(maxIterations)
            .withProfiles(userEnabledProfiles)
            .withForceVersion(userForceVersions)
            .withTypelevel(typelevel)
        ),
        verbosityLevel,
        log
      )

      resOrError match {
        case Left(err) =>
          throw err
        case Right(res) =>
          res
      }
    }
  }

}
