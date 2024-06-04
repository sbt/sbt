package coursier.sbtcoursier

import coursier.ProjectCache
import coursier.cache.FileCache
import coursier.core._
import coursier.internal.Typelevel
import lmcoursier.definitions.ToCoursier
import lmcoursier.{FallbackDependency, FromSbt, Inputs}
import lmcoursier.internal.{InterProjectRepository, ResolutionParams, ResolutionRun, Resolvers}
import coursier.sbtcoursier.Keys._
import coursier.sbtcoursiershared.InputsTasks.{credentialsTask, strictTask}
import coursier.sbtcoursiershared.SbtCoursierShared.autoImport._
import coursier.util.{ModuleMatcher, ModuleMatchers}
import sbt.Def
import sbt.Keys._

object ResolutionTasks {

  def resolutionsTask(
    sbtClassifiers: Boolean = false,
    missingOk: Boolean = false,
  ): Def.Initialize[sbt.Task[Map[Configuration, coursier.Resolution]]] = {

    val currentProjectTask: sbt.Def.Initialize[sbt.Task[(Project, Seq[FallbackDependency], Seq[(Configuration, Seq[Configuration])])]] =
      if (sbtClassifiers)
        Def.task {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value
          val cm = coursierSbtClassifiersModule.value
          val proj = ToCoursier.project(SbtCoursierFromSbt.sbtClassifiersProject(cm, sv, sbv))

          val fallbackDeps = FromSbt.fallbackDependencies(
            cm.dependencies,
            sv,
            sbv
          )

          (proj, fallbackDeps, cm.configurations.map(c => Configuration(c.name) -> Nil))
        }
      else
        Def.task {
          val baseConfigGraphs = coursierConfigGraphs.value
          val publications = coursierPublications.value
          (ToCoursier.project(coursierProject.value.withPublications(publications)), coursierFallbackDependencies.value, baseConfigGraphs)
        }

    val resolversTask =
      if (sbtClassifiers)
        Def.task(coursierSbtResolvers.value)
      else
        Def.task(coursierRecursiveResolvers.value.distinct)

    val retrySettings = Def.task(coursierRetry.value)

    Def.task {
      val projectName = thisProjectRef.value.project

      val sv = scalaVersion.value
      val sbv = scalaBinaryVersion.value

      val interProjectDependencies = coursierInterProjectDependencies.value.map(ToCoursier.project)
      val extraProjects = coursierExtraProjects.value.map(ToCoursier.project)

      val parallelDownloads = coursierParallelDownloads.value
      val checksums = coursierChecksums.value
      val maxIterations = coursierMaxIterations.value
      val cachePolicies = coursierCachePolicies.value
      val ttl = coursierTtl.value
      val cache = coursierCache.value
      val createLogger = coursierLogger.value.map(ToCoursier.cacheLogger)

      val log = streams.value.log

      // are these always defined? (e.g. for Java only projects?)
      val so = Organization(scalaOrganization.value)

      val userForceVersions = Inputs.forceVersions(dependencyOverrides.value, sv, sbv)

      val verbosityLevel = coursierVerbosity.value

      val userEnabledProfiles = mavenProfiles.value
      val versionReconciliations0 = versionReconciliation.value.map { mod =>
        Reconciliation(mod.revision) match {
          case Some(rec) =>
            val (mod0, _) = FromSbt.moduleVersion(mod, sv, sbv)
            val matcher = ModuleMatchers.only(Organization(mod0.organization.value), ModuleName(mod0.name.value))
            matcher -> rec
          case None =>
            throw new Exception(s"Unrecognized reconciliation: '${mod.revision}'")
        }
      }

      val typelevel = Organization(scalaOrganization.value) == Typelevel.typelevelOrg

      val interProjectRepo = InterProjectRepository(interProjectDependencies)
      val extraProjectsRepo = InterProjectRepository(extraProjects)

      val ivyProperties = ResolutionParams.defaultIvyProperties(ivyPaths.value.ivyHome)

      val authenticationByRepositoryId = actualCoursierCredentials.value.mapValues(_.authentication)

      val (currentProject, fallbackDependencies, orderedConfigs) = currentProjectTask.value

      val autoScalaLib = autoScalaLibrary.value && scalaModuleInfo.value.forall(_.overrideScalaVersion)

      val resolvers = resolversTask.value

      // TODO Warn about possible duplicated modules from source repositories?

      val credentials = credentialsTask.value.map(ToCoursier.credentials)

      val strictOpt = strictTask.value.map(ToCoursier.strict)

      val parentProjectCache: ProjectCache = coursierParentProjectCache.value
        .get(resolvers)
        .map(_.foldLeft[ProjectCache](Map.empty)(_ ++ _))
        .getOrElse(Map.empty)

      val excludeDeps = Inputs.exclusions(
        coursier.sbtcoursiershared.InputsTasks.actualExcludeDependencies.value,
        sv,
        sbv,
        log
      ).map {
        case (org, name) =>
          (Organization(org.value), ModuleName(name.value))
      }

      val mainRepositories = resolvers
        .flatMap { resolver =>
          Resolvers.repository(
            resolver,
            ivyProperties,
            log,
            authenticationByRepositoryId.get(resolver.name).map { a =>
              Authentication(a.user, a.password)
                .withOptional(a.optional)
                .withRealmOpt(a.realmOpt)
            },
            Seq(),
          )
        }

      val resOrError = ResolutionRun.resolutions(
        ResolutionParams(
          dependencies = currentProject.dependencies,
          fallbackDependencies = fallbackDependencies,
          orderedConfigs = orderedConfigs,
          autoScalaLibOpt = if (autoScalaLib) Some((so, sv)) else None,
          mainRepositories = mainRepositories,
          parentProjectCache = parentProjectCache,
          interProjectDependencies = interProjectDependencies,
          internalRepositories = Seq(interProjectRepo, extraProjectsRepo),
          sbtClassifiers = sbtClassifiers,
          projectName = projectName,
          loggerOpt = createLogger,
          cache = FileCache()
            .withLocation(cache)
            .withCachePolicies(cachePolicies)
            .withTtl(ttl)
            .withChecksums(checksums)
            .withCredentials(credentials)
            .withFollowHttpToHttpsRedirections(true),
          parallel = parallelDownloads,
          params = coursier.params.ResolutionParams()
            .withMaxIterations(maxIterations)
            .withProfiles(userEnabledProfiles)
            .withForceVersion(userForceVersions.map { case (k, v) => (ToCoursier.module(k), v) }.toMap)
            .withTypelevel(typelevel)
            .addReconciliation(versionReconciliations0: _*)
            .withExclusions(excludeDeps),
          strictOpt = strictOpt,
          missingOk = missingOk,
          retry = retrySettings.value.getOrElse(ResolutionParams.defaultRetry)
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
