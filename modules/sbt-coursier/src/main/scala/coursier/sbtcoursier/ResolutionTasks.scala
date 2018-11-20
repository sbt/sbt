package coursier.sbtcoursier

import java.net.URL

import coursier.{Cache, ProjectCache}
import coursier.core._
import coursier.extra.Typelevel
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.sbtcoursier.Keys._
import sbt.Def
import sbt.Keys._

import scala.util.Try

object ResolutionTasks {

  def resolutionsTask(
    sbtClassifiers: Boolean = false
  ): Def.Initialize[sbt.Task[Map[Set[Configuration], coursier.Resolution]]] = {

    val currentProjectTask: sbt.Def.Initialize[sbt.Task[(Project, Seq[(Module, String, URL, Boolean)], Seq[Set[Configuration]])]] =
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

    val authenticationByHostTask = Def.taskDyn {

      val useSbtCredentials = coursierUseSbtCredentials.value

      if (useSbtCredentials)
        Def.task {
          val log = streams.value.log

          sbt.Keys.credentials.value
            .flatMap {
              case dc: sbt.DirectCredentials => List(dc)
              case fc: sbt.FileCredentials =>
                sbt.Credentials.loadCredentials(fc.path) match {
                  case Left(err) =>
                    log.warn(s"$err, ignoring it")
                    Nil
                  case Right(dc) => List(dc)
                }
            }
            .map { c =>
              c.host -> Authentication(c.userName, c.passwd)
            }
            .toMap
        }
      else
        Def.task(Map.empty[String, Authentication])
    }

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
      val createLogger = coursierCreateLogger.value

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

      val globalPluginsRepos =
        for (p <- ResolutionParams.globalPluginPatterns(sbtBinaryVersion.value))
          yield IvyRepository.fromPattern(
            p,
            withChecksums = false,
            withSignatures = false,
            withArtifacts = false
          )

      val interProjectRepo = InterProjectRepository(interProjectDependencies)

      val ivyProperties = ResolutionParams.defaultIvyProperties()

      val authenticationByRepositoryId = coursierCredentials.value.mapValues(_.authentication)

      val (currentProject, fallbackDependencies, configGraphs) = currentProjectTask.value

      val autoScalaLib = autoScalaLibrary.value

      val resolvers = resolversTask.value

      // TODO Warn about possible duplicated modules from source repositories?

      val authenticationByHost = authenticationByHostTask.value

      val internalRepositories = globalPluginsRepos :+ interProjectRepo

      val parentProjectCache: ProjectCache = coursierParentProjectCache.value
        .get(resolvers)
        .map(_.foldLeft[ProjectCache](Map.empty)(_ ++ _))
        .getOrElse(Map.empty)

      def withAuthenticationByHost(repo: Repository, credentials: Map[String, Authentication]): Repository = {

        def httpHost(s: String) =
          if (s.startsWith("http://") || s.startsWith("https://"))
            Try(Cache.url(s).getHost).toOption
          else
            None

        repo match {
          case m: MavenRepository =>
            if (m.authentication.isEmpty)
              httpHost(m.root).flatMap(credentials.get).fold(m) { auth =>
                m.copy(authentication = Some(auth))
              }
            else
              m
          case i: IvyRepository =>
            if (i.authentication.isEmpty) {
              val base = i.pattern.chunks.takeWhile {
                case _: coursier.ivy.Pattern.Chunk.Const => true
                case _ => false
              }.map(_.string).mkString

              httpHost(base).flatMap(credentials.get).fold(i) { auth =>
                i.copy(authentication = Some(auth))
              }
            } else
              i
          case _ =>
            repo
        }
      }

      val mainRepositories = resolvers
        .flatMap { resolver =>
          FromSbt.repository(
            resolver,
            ivyProperties,
            log,
            authenticationByRepositoryId.get(resolver.name)
          )
        }
        .map(withAuthenticationByHost(_, authenticationByHost))

      val resOrError = ResolutionRun.resolutions(
        ResolutionParams(
          currentProject.dependencies,
          fallbackDependencies,
          configGraphs,
          autoScalaLib,
          mainRepositories,
          parentProjectCache,
          interProjectDependencies,
          internalRepositories,
          userEnabledProfiles,
          userForceVersions,
          typelevel,
          so,
          sv,
          sbtClassifiers,
          parallelDownloads,
          projectName,
          maxIterations,
          createLogger,
          cache,
          cachePolicies,
          ttl,
          checksums
        ),
        verbosityLevel,
        log
      )

      resOrError match {
        case Left(err) =>
          err.throwException()
        case Right(res) =>
          res
      }
    }
  }

}
