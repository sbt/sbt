package coursier

import java.io.{OutputStreamWriter, File}
import java.util.concurrent.Executors

import coursier.cli.TermDisplay
import coursier.ivy.IvyRepository
import sbt.{Classpaths, Resolver, Def}
import Structure._
import Keys._
import sbt.Keys._

import scalaz.{\/-, -\/}
import scalaz.concurrent.{ Task, Strategy }

object Tasks {

  def coursierResolversTask: Def.Initialize[sbt.Task[Seq[Resolver]]] = Def.task {
    var l = externalResolvers.value
    if (sbtPlugin.value)
      l = Seq(
        sbtResolver.value,
        Classpaths.sbtPluginReleases
      ) ++ l
    l
  }

  def coursierProjectTask: Def.Initialize[sbt.Task[(Project, Seq[(String, Seq[Artifact])])]] =
    (
      sbt.Keys.state,
      sbt.Keys.thisProjectRef
    ).flatMap { (state, projectRef) =>

      // should projectID.configurations be used instead?
      val configurations = ivyConfigurations.in(projectRef).get(state)

      // exportedProducts looks like what we want, but depends on the update task, which
      // make the whole thing run into cycles...
      val artifacts = configurations.map { cfg =>
        cfg.name -> Option(classDirectory.in(projectRef).in(cfg).getOrElse(state, null))
      }.collect { case (name, Some(classDir)) =>
        name -> Seq(
          Artifact(
            classDir.toURI.toString,
            Map.empty,
            Map.empty,
            Attributes(),
            changing = true
          )
        )
      }

      val allDependenciesTask = allDependencies.in(projectRef).get(state)

      for {
        allDependencies <- allDependenciesTask
      } yield {

        val proj = FromSbt.project(
          projectID.in(projectRef).get(state),
          allDependencies,
          configurations.map { cfg => cfg.name -> cfg.extendsConfigs.map(_.name) }.toMap,
          scalaVersion.in(projectRef).get(state),
          scalaBinaryVersion.in(projectRef).get(state)
        )

        (proj, artifacts)
      }
    }

  def coursierProjectsTask: Def.Initialize[sbt.Task[Seq[(Project, Seq[(String, Seq[Artifact])])]]] =
    sbt.Keys.state.flatMap { state =>
      val projects = structure(state).allProjectRefs
      coursierProject.forAllProjects(state, projects).map(_.values.toVector)
    }

  def updateTask(withClassifiers: Boolean, sbtClassifiers: Boolean = false) = Def.task {

    def errPrintln(s: String): Unit = scala.Console.err.println(s)

    def grouped[K, V](map: Seq[(K, V)]): Map[K, Seq[V]] =
      map.groupBy { case (k, _) => k }.map {
        case (k, l) =>
          k -> l.map { case (_, v) => v }
      }

    val ivyProperties = Map(
      "ivy.home" -> s"${sys.props("user.home")}/.ivy2"
    ) ++ sys.props

    def createLogger() = Some {
      new TermDisplay(
        new OutputStreamWriter(System.err),
        fallbackMode = sys.env.get("COURSIER_NO_TERM").nonEmpty
      )
    }

    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    synchronized {

      lazy val cm = coursierSbtClassifiersModule.value

      val currentProject =
        if (sbtClassifiers)
          FromSbt.project(
            cm.id,
            cm.modules,
            cm.configurations.map(cfg => cfg.name -> cfg.extendsConfigs.map(_.name)).toMap,
            scalaVersion.value,
            scalaBinaryVersion.value
          )
        else {
          val (p, _) = coursierProject.value
          p
        }

      val projects = coursierProjects.value

      val parallelDownloads = coursierParallelDownloads.value
      val checksums = coursierChecksums.value
      val artifactsChecksums = coursierArtifactsChecksums.value
      val maxIterations = coursierMaxIterations.value
      val cachePolicy = coursierCachePolicy.value
      val cacheDir = coursierCache.value

      val resolvers =
        if (sbtClassifiers)
          coursierSbtResolvers.value
        else
          coursierResolvers.value

      val verbosity = coursierVerbosity.value


      val startRes = Resolution(
        currentProject.dependencies.map { case (_, dep) => dep }.toSet,
        filter = Some(dep => !dep.optional),
        forceVersions = projects.map { case (proj, _) => proj.moduleVersion }.toMap
      )

      if (verbosity >= 1) {
        println("InterProjectRepository")
        for ((p, _) <- projects)
          println(s"  ${p.module}:${p.version}")
      }

      val globalPluginsRepo = IvyRepository(
        new File(sys.props("user.home") + "/.sbt/0.13/plugins/target/resolution-cache/").toURI.toString +
          "[organization]/[module](/scala_[scalaVersion])(/sbt_[sbtVersion])/[revision]/resolved.xml.[ext]",
        withChecksums = false,
        withSignatures = false,
        withArtifacts = false
      )

      val interProjectRepo = InterProjectRepository(projects)
      val repositories = Seq(globalPluginsRepo, interProjectRepo) ++ resolvers.flatMap(FromSbt.repository(_, ivyProperties))

      val caches = Seq(
        "http://" -> new File(cacheDir, "http"),
        "https://" -> new File(cacheDir, "https")
      )

      val pool = Executors.newFixedThreadPool(parallelDownloads, Strategy.DefaultDaemonThreadFactory)

      val logger = createLogger()
      logger.foreach(_.init())
      val fetch = coursier.Fetch(
        repositories,
        Cache.fetch(caches, CachePolicy.LocalOnly, checksums = checksums, logger = logger, pool = pool),
        Cache.fetch(caches, cachePolicy, checksums = checksums, logger = logger, pool = pool)
      )

      def depsRepr = currentProject.dependencies.map { case (config, dep) =>
        s"${dep.module}:${dep.version}:$config->${dep.configuration}"
      }.sorted

      if (verbosity >= 0)
        errPrintln(s"Resolving ${currentProject.module.organization}:${currentProject.module.name}:${currentProject.version}")
      if (verbosity >= 1)
        for (depRepr <- depsRepr.distinct)
          errPrintln(s"  $depRepr")

      val res = startRes
        .process
        .run(fetch, maxIterations)
        .attemptRun
        .leftMap(ex => throw new Exception(s"Exception during resolution", ex))
        .merge

      if (!res.isDone)
        throw new Exception(s"Maximum number of iteration reached!")

      if (verbosity >= 0)
        errPrintln("Resolution done")

      def repr(dep: Dependency) = {
        // dep.version can be an interval, whereas the one from project can't
        val version = res
          .projectCache
          .get(dep.moduleVersion)
          .map(_._2.version)
          .getOrElse(dep.version)
        val extra =
          if (version == dep.version) ""
          else s" ($version for ${dep.version})"

        (
          Seq(
            dep.module.organization,
            dep.module.name,
            dep.attributes.`type`
          ) ++
            Some(dep.attributes.classifier)
              .filter(_.nonEmpty)
              .toSeq ++
            Seq(
              version
            )
          ).mkString(":") + extra
      }

      if (res.conflicts.nonEmpty) {
        // Needs test
        println(s"${res.conflicts.size} conflict(s):\n  ${res.conflicts.toList.map(repr).sorted.mkString("  \n")}")
      }

      val errors = res.errors

      if (errors.nonEmpty) {
        println(s"\n${errors.size} error(s):")
        for ((dep, errs) <- errors) {
          println(s"  ${dep.module}:${dep.version}:\n${errs.map("    " + _.replace("\n", "    \n")).mkString("\n")}")
        }
        throw new Exception(s"Encountered ${errors.length} error(s)")
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

      val allArtifacts =
        classifiers match {
          case None => res.artifacts
          case Some(cl) => res.classifiersArtifacts(cl)
        }

      val artifactFileOrErrorTasks = allArtifacts.toVector.map { a =>
        Cache.file(a, caches, cachePolicy, checksums = artifactsChecksums, logger = logger, pool = pool).run.map((a, _))
      }

      if (verbosity >= 0)
        errPrintln(s"Fetching artifacts")

      val artifactFilesOrErrors = Task.gatherUnordered(artifactFileOrErrorTasks).attemptRun match {
        case -\/(ex) =>
          throw new Exception(s"Error while downloading / verifying artifacts", ex)
        case \/-(l) =>
          l.toMap
      }

      if (verbosity >= 0)
        errPrintln(s"Fetching artifacts: done")

      val configs = {
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

        configs0.map {
          case (config, _) =>
            config -> allExtends(config)
        }
      }

      def artifactFileOpt(artifact: Artifact) = {
        val fileOrError = artifactFilesOrErrors.getOrElse(artifact, -\/("Not downloaded"))

        fileOrError match {
          case \/-(file) =>
            if (file.toString.contains("file:/"))
              throw new Exception(s"Wrong path: $file")
            Some(file)
          case -\/(err) =>
            errPrintln(s"${artifact.url}: $err")
            None
        }
      }

      val depsByConfig = grouped(currentProject.dependencies)

      ToSbt.updateReport(
        depsByConfig,
        res,
        configs,
        classifiers,
        artifactFileOpt
      )
    }
  }

}
