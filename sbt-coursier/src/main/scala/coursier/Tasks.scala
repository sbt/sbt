package coursier

import java.io.{File, OutputStreamWriter}
import java.net.URL
import java.util.concurrent.{ExecutorService, Executors}

import coursier.core.{Authentication, Publication}
import coursier.extra.Typelevel
import coursier.ivy.{IvyRepository, PropertiesPattern}
import coursier.Keys._
import coursier.Structure._
import coursier.util.{Config, Print}
import sbt.{Classpaths, Def, Resolver, UpdateReport}
import sbt.Keys._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.util.Try
import scalaz.{-\/, \/-}
import scalaz.concurrent.{Strategy, Task}

object Tasks {

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
      for (p <- structure(state).allProjects)
        yield p.id -> p.dependencies.map(_.project.project)

    val deps = dependencies(allProjectsDeps.toMap, projectRef.project)

    structure(state).allProjectRefs.filter(p => deps(p.project))
  }

  def coursierResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] = Def.task {

    val extRes = externalResolvers.value
    val isSbtPlugin = sbtPlugin.value
    val sbtRes = sbtResolver.value
    val bootResOpt = bootResolvers.value
    val overrideFlag = overrideBuildResolvers.value

    bootResOpt.filter(_ => overrideFlag).getOrElse {
      var resolvers = extRes
      if (isSbtPlugin)
        resolvers = Seq(
          sbtRes,
          Classpaths.sbtPluginReleases
        ) ++ resolvers
      resolvers
    }
  }

  def coursierRecursiveResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] =
    (
      sbt.Keys.state,
      sbt.Keys.thisProjectRef
    ).flatMap { (state, projectRef) =>

      val projects = allRecursiveInterDependencies(state, projectRef)

      coursierResolvers
        .forAllProjects(state, projectRef +: projects)
        .map(_.values.toVector.flatten)
    }

  def coursierFallbackDependenciesTask: Def.Initialize[sbt.Task[Seq[(Module, String, URL, Boolean)]]] =
    (
      sbt.Keys.state,
      sbt.Keys.thisProjectRef
    ).flatMap { (state, projectRef) =>

      val projects = allRecursiveInterDependencies(state, projectRef)

      val allDependenciesTask = allDependencies
        .forAllProjects(state, projectRef +: projects)
        .map(_.values.toVector.flatten)

      for {
        allDependencies <- allDependenciesTask
      } yield {

        FromSbt.fallbackDependencies(
          allDependencies,
          scalaVersion.in(projectRef).get(state),
          scalaBinaryVersion.in(projectRef).get(state)
        )
      }
    }

  def coursierProjectTask: Def.Initialize[sbt.Task[Project]] =
    (
      sbt.Keys.state,
      sbt.Keys.thisProjectRef
    ).flatMap { (state, projectRef) =>

      // should projectID.configurations be used instead?
      val configurations = ivyConfigurations.in(projectRef).get(state)

      val allDependenciesTask = allDependencies.in(projectRef).get(state)

      lazy val projId = projectID.in(projectRef).get(state)
      lazy val sv = scalaVersion.in(projectRef).get(state)
      lazy val sbv = scalaBinaryVersion.in(projectRef).get(state)

      lazy val exclusions = {

        var anyNonSupportedExclusionRule = false

        val res = excludeDependencies
          .in(projectRef)
          .get(state)
          .flatMap { rule =>
            if (rule.artifact != "*" || rule.configurations.nonEmpty) {
              state.log.warn(s"Unsupported exclusion rule $rule")
              anyNonSupportedExclusionRule = true
              Nil
            } else
              Seq(
                (rule.organization, FromSbt.sbtCrossVersionName(rule.name, rule.crossVersion, sv, sbv))
              )
          }
          .toSet

        if (anyNonSupportedExclusionRule)
          state.log.warn("Only supported exclusion rule fields: organization, name")

        res
      }

      for {
        allDependencies <- allDependenciesTask
      } yield {

        val configMap = configurations.map(cfg => cfg.name -> cfg.extendsConfigs.map(_.name)).toMap

        val proj = FromSbt.project(
          projId,
          allDependencies,
          configMap,
          sv,
          sbv
        )

        proj.copy(
          dependencies = proj.dependencies.map {
            case (config, dep) =>
              (config, dep.copy(exclusions = dep.exclusions ++ exclusions))
          }
        )
      }
    }

  def coursierInterProjectDependenciesTask: Def.Initialize[sbt.Task[Seq[Project]]] =
    (
      sbt.Keys.state,
      sbt.Keys.thisProjectRef
    ).flatMap { (state, projectRef) =>

      val projects = allRecursiveInterDependencies(state, projectRef)

      coursierProject.forAllProjects(state, projects).map(_.values.toVector)
    }

  def coursierPublicationsTask(
    configsMap: (sbt.Configuration, String)*
  ): Def.Initialize[sbt.Task[Seq[(String, Publication)]]] =
    Def.task {

      val state = sbt.Keys.state.value
      val projectRef = sbt.Keys.thisProjectRef.value
      val projId = sbt.Keys.projectID.value
      val sv = sbt.Keys.scalaVersion.value
      val sbv = sbt.Keys.scalaBinaryVersion.value
      val ivyConfs = sbt.Keys.ivyConfigurations.value

      val sourcesConfigOpt =
        if (ivyConfigurations.value.exists(_.name == "sources"))
          Some("sources")
        else
          None

      val docsConfigOpt =
        if (ivyConfigurations.value.exists(_.name == "docs"))
          Some("docs")
        else
          None

      val sbtBinArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish = publishArtifact
            .in(projectRef)
            .in(packageBin)
            .in(config)
            .getOrElse(state, false)

          if (publish)
            artifact
              .in(projectRef)
              .in(packageBin)
              .in(config)
              .find(state)
              .map(targetConfig -> _)
          else
            None
        }

      val sbtSourceArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish = publishArtifact
            .in(projectRef)
            .in(packageSrc)
            .in(config)
            .getOrElse(state, false)

          if (publish)
            artifact
              .in(projectRef)
              .in(packageSrc)
              .in(config)
              .find(state)
              .map(sourcesConfigOpt.getOrElse(targetConfig) -> _)
          else
            None
        }

      val sbtDocArtifacts =
        for ((config, targetConfig) <- configsMap) yield {

          val publish = publishArtifact
            .in(projectRef)
            .in(packageDoc)
            .in(config)
            .getOrElse(state, false)

          if (publish)
            artifact
              .in(projectRef)
              .in(packageDoc)
              .in(config)
              .find(state)
              .map(docsConfigOpt.getOrElse(targetConfig) -> _)
          else
            None
        }

      val sbtArtifacts = sbtBinArtifacts ++ sbtSourceArtifacts ++ sbtDocArtifacts

      def artifactPublication(artifact: sbt.Artifact) = {

        val name = FromSbt.sbtCrossVersionName(
          artifact.name,
          projId.crossVersion,
          sv,
          sbv
        )

        Publication(
          name,
          artifact.`type`,
          artifact.extension,
          artifact.classifier.getOrElse("")
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
      val extraSbtArtifacts = artifacts.in(projectRef).getOrElse(state, Nil)
        .filterNot(stdArtifactsSet)

      // Seems that SBT does that - if an artifact has no configs,
      // it puts it in all of them. See for example what happens to
      // the standalone JAR artifact of the coursier cli module.
      def allConfigsIfEmpty(configs: Iterable[sbt.Configuration]): Iterable[sbt.Configuration] =
        if (configs.isEmpty) ivyConfs else configs

      val extraSbtArtifactsPublication = for {
        artifact <- extraSbtArtifacts
        config <- allConfigsIfEmpty(artifact.configurations) if config.isPublic
      } yield config.name -> artifactPublication(artifact)

      sbtArtifactsPublication ++ extraSbtArtifactsPublication
    }

  def coursierConfigurationsTask(shadedConfig: Option[(String, String)]) = Def.task {

    val configs0 = ivyConfigurations.value.map { config =>
      config.name -> config.extendsConfigs.map(_.name)
    }.toMap

    def allExtends(c: String) = {
      // possibly bad complexity
      def helper(current: Set[String]): Set[String] = {
        val newSet = current ++ current.flatMap(configs0.getOrElse(_, Nil))
        if ((newSet -- current).nonEmpty)
          helper(newSet)
        else
          newSet
      }

      helper(Set(c))
    }

    val map = configs0.map {
      case (config, _) =>
        config -> allExtends(config)
    }

    map ++ shadedConfig.toSeq.flatMap {
      case (baseConfig, shadedConfig) =>
        Seq(
          baseConfig -> (map.getOrElse(baseConfig, Set(baseConfig)) + shadedConfig),
          shadedConfig -> map.getOrElse(shadedConfig, Set(shadedConfig))
        )
    }
  }

  private final case class ResolutionCacheKey(
    project: Project,
    repositories: Seq[Repository],
    userEnabledProfiles: Set[String],
    resolution: Resolution,
    sbtClassifiers: Boolean
  )

  private final case class ReportCacheKey(
    project: Project,
    resolution: Resolution,
    withClassifiers: Boolean,
    sbtClassifiers: Boolean
  )

  private val resolutionsCache = new mutable.HashMap[ResolutionCacheKey, Resolution]
  // these may actually not need to be cached any more, now that the resolutions
  // are cached
  private val reportsCache = new mutable.HashMap[ReportCacheKey, UpdateReport]

  private def forcedScalaModules(
    scalaOrganization: String,
    scalaVersion: String
  ): Map[Module, String] =
    Map(
      Module(scalaOrganization, "scala-library") -> scalaVersion,
      Module(scalaOrganization, "scala-compiler") -> scalaVersion,
      Module(scalaOrganization, "scala-reflect") -> scalaVersion,
      Module(scalaOrganization, "scalap") -> scalaVersion
    )

  private def createLogger() = new TermDisplay(new OutputStreamWriter(System.err))

  private lazy val globalPluginPattern = {

    val props = sys.props.toMap

    val extraProps = new ArrayBuffer[(String, String)]

    def addUriProp(key: String): Unit =
      for (b <- props.get(key)) {
        val uri = new File(b).toURI.toString
        extraProps += s"$key.uri" -> uri
      }

    addUriProp("sbt.global.base")
    addUriProp("user.home")

    // FIXME get the 0.13 automatically?
    val s = s"$${sbt.global.base.uri-$${user.home.uri}/.sbt/0.13}/plugins/target/resolution-cache/" +
      "[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/resolved.xml.[ext]"

    val p = PropertiesPattern.parse(s) match {
      case -\/(err) =>
        throw new Exception(s"Cannot parse pattern $s: $err")
      case \/-(p) =>
        p
    }

    p.substituteProperties(props ++ extraProps) match {
      case -\/(err) =>
        throw new Exception(err)
      case \/-(p) =>
        p
    }
  }

  def parentProjectCacheTask: Def.Initialize[sbt.Task[Map[Seq[sbt.Resolver],Seq[coursier.ProjectCache]]]] =
    (sbt.Keys.state,
      sbt.Keys.thisProjectRef).flatMap{ (state, projectRef) =>

      val projectDeps = structure(state).allProjects
        .find(_.id == projectRef.project)
        .map(_.dependencies.map(_.project.project).toSet)
        .getOrElse(Set.empty)

      val projects = structure(state).allProjectRefs.filter(p => projectDeps(p.project))

      coursierRecursiveResolvers.forAllProjects(state, projects).flatMap{ m =>
        coursierResolution.forAllProjects(state, m.keys.toSeq).map{ n =>
          n.foldLeft(Map.empty[Seq[Resolver], Seq[ProjectCache]]){ case (caches, (ref, resolution)) =>
            m.get(ref).fold(caches)(resolvers =>
              caches.updated(resolvers, resolution.projectCache +: caches.getOrElse(resolvers, Seq.empty)))
          }
        }
      }
    }

  private val noOptionalFilter: Option[Dependency => Boolean] = Some(dep => !dep.optional)
  private val typelevelOrgSwap: Option[Dependency => Dependency] = Some(Typelevel.swap(_))


  def resolutionTask(
    sbtClassifiers: Boolean = false
  ): Def.Initialize[sbt.Task[coursier.Resolution]] = Def.task {

    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    synchronized {

      lazy val cm = coursierSbtClassifiersModule.value

      lazy val projectName = thisProjectRef.value.project

      val (currentProject, fallbackDependencies) =
        if (sbtClassifiers) {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value

          val proj = FromSbt.project(
            cm.id,
            cm.modules,
            cm.configurations.map(cfg => cfg.name -> cfg.extendsConfigs.map(_.name)).toMap,
            sv,
            sbv
          )

          val fallbackDeps = FromSbt.fallbackDependencies(
            cm.modules,
            sv,
            sbv
          )

          (proj, fallbackDeps)
        } else {
          val proj = coursierProject.value
          val publications = coursierPublications.value
          val fallbackDeps = coursierFallbackDependencies.value
          (proj.copy(publications = publications), fallbackDeps)
        }

      val interProjectDependencies = coursierInterProjectDependencies.value

      val parallelDownloads = coursierParallelDownloads.value
      val checksums = coursierChecksums.value
      val maxIterations = coursierMaxIterations.value
      val cachePolicies = coursierCachePolicies.value
      val ttl = coursierTtl.value
      val cache = coursierCache.value

      val log = streams.value.log

      // are these always defined? (e.g. for Java only projects?)
      val so = scalaOrganization.value
      val sv = scalaVersion.value
      val sbv = scalaBinaryVersion.value

      val userForceVersions = dependencyOverrides.value.map(
        FromSbt.moduleVersion(_, sv, sbv)
      ).toMap

      val resolvers =
        if (sbtClassifiers)
          coursierSbtResolvers.value
        else
          coursierRecursiveResolvers.value.distinct

      val parentProjectCache: ProjectCache = coursierParentProjectCache.value
          .get(resolvers)
          .map(_.foldLeft[ProjectCache](Map.empty)(_ ++ _))
          .getOrElse(Map.empty)

      // TODO Warn about possible duplicated modules from source repositories?

      val verbosityLevel = coursierVerbosity.value

      val userEnabledProfiles = mavenProfiles.value

      val typelevel = scalaOrganization.value == Typelevel.typelevelOrg

      val startRes = Resolution(
        currentProject.dependencies.map(_._2).toSet,
        filter = noOptionalFilter,
        userActivations =
          if (userEnabledProfiles.isEmpty)
            None
          else
            Some(userEnabledProfiles.iterator.map(_ -> true).toMap),
        forceVersions =
          // order matters here
          userForceVersions ++
          forcedScalaModules(so, sv) ++
          interProjectDependencies.map(_.moduleVersion),
        projectCache = parentProjectCache,
        mapDependencies = if (typelevel) typelevelOrgSwap else None
      )

      if (verbosityLevel >= 2) {
        log.info("InterProjectRepository")
        for (p <- interProjectDependencies)
          log.info(s"  ${p.module}:${p.version}")
      }

      val globalPluginsRepo = IvyRepository.fromPattern(
        globalPluginPattern,
        withChecksums = false,
        withSignatures = false,
        withArtifacts = false
      )

      val interProjectRepo = InterProjectRepository(interProjectDependencies)

      val ivyHome = sys.props.getOrElse(
        "ivy.home",
        new File(sys.props("user.home")).toURI.getPath + ".ivy2"
      )

      val sbtIvyHome = sys.props.getOrElse(
        "sbt.ivy.home",
        ivyHome
      )

      val ivyProperties = Map(
        "ivy.home" -> ivyHome,
        "sbt.ivy.home" -> sbtIvyHome
      ) ++ sys.props

      val useSbtCredentials = coursierUseSbtCredentials.value

      val authenticationByHost =
        if (useSbtCredentials)
          sbt.Keys.credentials
            .value
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
        else
          Map.empty[String, Authentication]

      val authenticationByRepositoryId = coursierCredentials.value.mapValues(_.authentication)

      val fallbackDependenciesRepositories =
        if (fallbackDependencies.isEmpty)
          Nil
        else {
          val map = fallbackDependencies.map {
            case (mod, ver, url, changing) =>
              (mod, ver) -> ((url, changing))
          }.toMap

          Seq(
            FallbackDependenciesRepository(map)
          )
        }

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

      val internalRepositories = Seq(globalPluginsRepo, interProjectRepo)

      val repositories =
        internalRepositories ++
        resolvers.flatMap { resolver =>
          FromSbt.repository(
            resolver,
            ivyProperties,
            log,
            authenticationByRepositoryId.get(resolver.name)
          )
        }.map(withAuthenticationByHost(_, authenticationByHost)) ++
        fallbackDependenciesRepositories

      def resolution = {
        var pool: ExecutorService = null
        var resLogger: TermDisplay = null

        val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

        val res = try {
          pool = Executors.newFixedThreadPool(parallelDownloads, Strategy.DefaultDaemonThreadFactory)
          resLogger = createLogger()

          val fetch = Fetch.from(
            repositories,
            Cache.fetch(cache, cachePolicies.head, checksums = checksums, logger = Some(resLogger), pool = pool, ttl = ttl),
            cachePolicies.tail.map(p =>
              Cache.fetch(cache, p, checksums = checksums, logger = Some(resLogger), pool = pool, ttl = ttl)
            ): _*
          )

          def depsRepr(deps: Seq[(String, Dependency)]) =
            deps.map { case (config, dep) =>
              s"${dep.module}:${dep.version}:$config->${dep.configuration}"
            }.sorted.distinct

          if (verbosityLevel >= 2) {
            val repoReprs = repositories.map {
              case r: IvyRepository =>
                s"ivy:${r.pattern}"
              case r: InterProjectRepository =>
                "inter-project"
              case r: MavenRepository =>
                r.root
              case r =>
                // should not happen
                r.toString
            }

            log.info(
              "Repositories:\n" +
                repoReprs.map("  " + _).mkString("\n")
            )
          }

          val initialMessage =
            Seq(
              if (verbosityLevel >= 0)
                Seq(s"Updating $projectName" + (if (sbtClassifiers) " (sbt classifiers)" else ""))
              else
                Nil,
              if (verbosityLevel >= 2)
                depsRepr(currentProject.dependencies).map(depRepr =>
                  s"  $depRepr"
                )
              else
                Nil
            ).flatten.mkString("\n")

          if (verbosityLevel >= 2)
            log.info(initialMessage)

          resLogger.init(if (printOptionalMessage) log.info(initialMessage))

          startRes
            .process
            .run(fetch, maxIterations)
            .unsafePerformSyncAttempt
            .leftMap(ex =>
              ResolutionError.UnknownException(ex)
                .throwException()
            )
            .merge
        } finally {
          if (pool != null)
            pool.shutdown()
          if (resLogger != null)
            if ((resLogger.stopDidPrintSomething() && printOptionalMessage) || verbosityLevel >= 2)
              log.info(s"Resolved $projectName dependencies")
        }

        if (!res.isDone)
          ResolutionError.MaximumIterationsReached
            .throwException()

        if (res.conflicts.nonEmpty) {
          val projCache = res.projectCache.mapValues { case (_, p) => p }

          ResolutionError.Conflicts(
            "Conflict(s) in dependency resolution:\n  " +
              Print.dependenciesUnknownConfigs(res.conflicts.toVector, projCache)
          ).throwException()
        }

        if (res.metadataErrors.nonEmpty) {
          val internalRepositoriesLen = internalRepositories.length
          val errors =
            if (repositories.length > internalRepositoriesLen)
            // drop internal repository errors
              res.metadataErrors.map {
                case (dep, errs) =>
                  dep -> errs.drop(internalRepositoriesLen)
              }
            else
              res.metadataErrors

          ResolutionError.MetadataDownloadErrors(errors)
            .throwException()
        }

        res
      }

      resolutionsCache.getOrElseUpdate(
        ResolutionCacheKey(
          currentProject,
          repositories,
          userEnabledProfiles,
          startRes.copy(filter = None),
          sbtClassifiers
        ),
        resolution
      )
    }
  }

  def artifactFilesOrErrors(
    withClassifiers: Boolean,
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false
  ) = Def.task {

    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    synchronized {

      lazy val cm = coursierSbtClassifiersModule.value

      lazy val projectName = thisProjectRef.value.project

      val parallelDownloads = coursierParallelDownloads.value
      val artifactsChecksums = coursierArtifactsChecksums.value
      val cachePolicies = coursierCachePolicies.value
      val ttl = coursierTtl.value
      val cache = coursierCache.value

      val log = streams.value.log

      val verbosityLevel = coursierVerbosity.value

      val res = {
        if (withClassifiers && sbtClassifiers)
          coursierSbtClassifiersResolution
        else
          coursierResolution
      }.value

      val classifiers =
        if (withClassifiers)
          Some {
            if (sbtClassifiers)
              cm.classifiers
            else
              transitiveClassifiers.value
          }
        else
          None

      val allArtifacts =
        classifiers match {
          case None => res.artifacts
          case Some(cl) => res.classifiersArtifacts(cl)
        }

      var pool: ExecutorService = null
      var artifactsLogger: TermDisplay = null

      val printOptionalMessage = verbosityLevel >= 0 && verbosityLevel <= 1

      val artifactFilesOrErrors = try {
        pool = Executors.newFixedThreadPool(parallelDownloads, Strategy.DefaultDaemonThreadFactory)
        artifactsLogger = createLogger()

        val artifactFileOrErrorTasks = allArtifacts.toVector.map { a =>
          def f(p: CachePolicy) =
            Cache.file(
              a,
              cache,
              p,
              checksums = artifactsChecksums,
              logger = Some(artifactsLogger),
              pool = pool,
              ttl = ttl
            )

          cachePolicies.tail
            .foldLeft(f(cachePolicies.head))(_ orElse f(_))
            .run
            .map((a, _))
        }

        val artifactInitialMessage =
          if (verbosityLevel >= 0)
            s"Fetching artifacts of $projectName" +
              (if (sbtClassifiers) " (sbt classifiers)" else "")
          else
            ""

        if (verbosityLevel >= 2)
          log.info(artifactInitialMessage)

        artifactsLogger.init(if (printOptionalMessage) log.info(artifactInitialMessage))

        Task.gatherUnordered(artifactFileOrErrorTasks).unsafePerformSyncAttempt match {
          case -\/(ex) =>
            ResolutionError.UnknownDownloadException(ex)
              .throwException()
          case \/-(l) =>
            l.toMap
        }
      } finally {
        if (pool != null)
          pool.shutdown()
        if (artifactsLogger != null)
          if ((artifactsLogger.stopDidPrintSomething() && printOptionalMessage) || verbosityLevel >= 2)
            log.info(
              s"Fetched artifacts of $projectName" +
                (if (sbtClassifiers) " (sbt classifiers)" else "")
            )
      }

      artifactFilesOrErrors
    }
  }

  private def artifactFileOpt(
    sbtBootJarOverrides: Map[(Module, String), File],
    artifactFiles: Map[Artifact, File],
    erroredArtifacts: Set[Artifact],
    log: sbt.Logger,
    module: Module,
    version: String,
    artifact: Artifact
  ) = {

    val artifact0 = artifact
      .copy(attributes = Attributes()) // temporary hack :-(

    // Under some conditions, SBT puts the scala JARs of its own classpath
    // in the application classpath. Ensuring we return SBT's jars rather than
    // JARs from the coursier cache, so that a same JAR doesn't land twice in the
    // application classpath (once via SBT jars, once via coursier cache).
    val fromBootJars =
      if (artifact.classifier.isEmpty && artifact.`type` == "jar")
        sbtBootJarOverrides.get((module, version))
      else
        None

    val res = fromBootJars.orElse(artifactFiles.get(artifact0))

    if (res.isEmpty && !erroredArtifacts(artifact0))
      log.error(s"${artifact.url} not downloaded (should not happen)")

    res
  }

  def updateTask(
    shadedConfigOpt: Option[(String, String)],
    withClassifiers: Boolean,
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false
  ) = Def.task {

    def grouped[K, V](map: Seq[(K, V)])(mapKey: K => K): Map[K, Seq[V]] =
      map.groupBy { case (k, _) => mapKey(k) }.map {
        case (k, l) =>
          k -> l.map { case (_, v) => v }
      }

    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    synchronized {

      val so = scalaOrganization.value
      val internalSbtScalaProvider = appConfiguration.value.provider.scalaProvider
      val sbtBootJarOverrides = SbtBootJars(
        so, // this seems plain wrong - this assumes that the scala org of the project is the same
        // as the one that started SBT. This will scrap the scala org specific JARs by the ones
        // that booted SBT, even if the latter come from the standard org.scala-lang org.
        // But SBT itself does it this way, and not doing so may make two different versions
        // of the scala JARs land in the classpath...
        internalSbtScalaProvider.version(),
        internalSbtScalaProvider.jars()
      )

      lazy val cm = coursierSbtClassifiersModule.value

      val currentProject =
        if (sbtClassifiers) {
          val sv = scalaVersion.value
          val sbv = scalaBinaryVersion.value

          FromSbt.project(
            cm.id,
            cm.modules,
            cm.configurations.map(cfg => cfg.name -> cfg.extendsConfigs.map(_.name)).toMap,
            sv,
            sbv
          )
        } else {
          val proj = coursierProject.value
          val publications = coursierPublications.value
          proj.copy(publications = publications)
        }

      val log = streams.value.log

      val verbosityLevel = coursierVerbosity.value

      val res = {
        if (withClassifiers && sbtClassifiers)
          coursierSbtClassifiersResolution
        else
          coursierResolution
      }.value

      def report = {

        val depsByConfig = grouped(currentProject.dependencies)(
          config =>
            shadedConfigOpt match {
              case Some((baseConfig, `config`)) =>
                baseConfig
              case _ =>
                config
            }
        )

        val configs = {
          val m = coursierConfigurations.value
          shadedConfigOpt.fold(m) {
            case (baseConfig, shadedConfig) =>
              (m - shadedConfig) + (
                baseConfig -> (m.getOrElse(baseConfig, Set()) - shadedConfig)
              )
          }
        }

        if (verbosityLevel >= 2) {
          val finalDeps = Config.dependenciesWithConfig(
            res,
            depsByConfig.map { case (k, l) => k -> l.toSet },
            configs
          )

          val projCache = res.projectCache.mapValues { case (_, p) => p }
          val repr = Print.dependenciesUnknownConfigs(finalDeps.toVector, projCache)
          log.info(repr.split('\n').map("  " + _).mkString("\n"))
        }

        val classifiers =
          if (withClassifiers)
            Some {
              if (sbtClassifiers)
                cm.classifiers
              else
                transitiveClassifiers.value
            }
          else
            None

        val artifactFilesOrErrors0 = (
          if (withClassifiers) {
            if (sbtClassifiers)
              Keys.coursierSbtClassifiersArtifacts
            else
              Keys.coursierClassifiersArtifacts
          } else
            Keys.coursierArtifacts
        ).value

        val artifactFiles = artifactFilesOrErrors0.collect {
          case (artifact, \/-(file)) =>
            artifact -> file
        }

        val artifactErrors = artifactFilesOrErrors0.toVector.collect {
          case (_, -\/(err)) =>
            err
        }

        if (artifactErrors.nonEmpty) {
          val error = ResolutionError.DownloadErrors(artifactErrors)

          if (ignoreArtifactErrors)
            log.warn(error.description(verbosityLevel >= 1))
          else
            error.throwException()
        }

        // can be non empty only if ignoreArtifactErrors is true
        val erroredArtifacts = artifactFilesOrErrors0.collect {
          case (artifact, -\/(_)) =>
            artifact
        }.toSet

        ToSbt.updateReport(
          depsByConfig,
          res,
          configs,
          classifiers,
          artifactFileOpt(
            sbtBootJarOverrides,
            artifactFiles,
            erroredArtifacts,
            log,
            _,
            _,
            _
          )
        )
      }

      reportsCache.getOrElseUpdate(
        ReportCacheKey(
          currentProject,
          res,
          withClassifiers,
          sbtClassifiers
        ),
        report
      )
    }
  }

  def coursierDependencyTreeTask(
    inverse: Boolean,
    sbtClassifiers: Boolean = false,
    ignoreArtifactErrors: Boolean = false
  ) = Def.task {

    lazy val projectName = thisProjectRef.value.project

    val currentProject =
      if (sbtClassifiers) {
        val cm = coursierSbtClassifiersModule.value
        val sv = scalaVersion.value
        val sbv = scalaBinaryVersion.value

        FromSbt.project(
          cm.id,
          cm.modules,
          cm.configurations.map(cfg => cfg.name -> cfg.extendsConfigs.map(_.name)).toMap,
          sv,
          sbv
        )
      } else {
        val proj = coursierProject.value
        val publications = coursierPublications.value
        proj.copy(publications = publications)
      }

    val res = {
      if (sbtClassifiers)
        coursierSbtClassifiersResolution
      else
        coursierResolution
    }.value

    val config = configuration.value.name
    val configs = coursierConfigurations.value

    val includedConfigs = configs.getOrElse(config, Set.empty) + config

    val dependencies0 = currentProject.dependencies.collect {
      case (cfg, dep) if includedConfigs(cfg) => dep
    }.sortBy { dep =>
      (dep.module.organization, dep.module.name, dep.version)
    }

    val subRes = res.subset(dependencies0.toSet)

    // use sbt logging?
    println(
      projectName + "\n" +
      Print.dependencyTree(
        dependencies0,
        subRes,
        printExclusions = true,
        inverse,
        colors = !sys.props.get("sbt.log.noformat").toSeq.contains("true")
      )
    )
  }

}
