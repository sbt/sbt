package coursier

import java.io.{ File, OutputStreamWriter }

import coursier.cli.TermDisplay
import coursier.ivy.IvyRepository
import sbt.{ MavenRepository => _, _ }
import sbt.Keys._

import scalaz.{ -\/, \/- }
import scalaz.concurrent.Task

object CoursierPlugin extends AutoPlugin {

  override def trigger = allRequirements

  override def requires = sbt.plugins.IvyPlugin

  private def errPrintln(s: String): Unit = scala.Console.err.println(s)

  object autoImport {
    val coursierParallelDownloads = Keys.coursierParallelDownloads
    val coursierMaxIterations = Keys.coursierMaxIterations
    val coursierChecksums = Keys.coursierChecksums
    val coursierCachePolicy = Keys.coursierCachePolicy
    val coursierVerbosity = Keys.coursierVerbosity
    val coursierResolvers = Keys.coursierResolvers
    val coursierCache = Keys.coursierCache
    val coursierProject = Keys.coursierProject
    val coursierProjects = Keys.coursierProjects
  }

  import autoImport._


  private val ivyProperties = Map(
    "ivy.home" -> s"${sys.props("user.home")}/.ivy2"
  ) ++ sys.props

  private def createLogger() = Some {
    new TermDisplay(
      new OutputStreamWriter(System.err),
      fallbackMode = sys.env.get("COURSIER_NO_TERM").nonEmpty
    )
  }


  private def updateTask(withClassifiers: Boolean) = Def.task {

    // let's update only one module at once, for a better output
    // Downloads are already parallel, no need to parallelize further anyway
    synchronized {

      val (currentProject, _) = coursierProject.value
      val projects = coursierProjects.value

      val parallelDownloads = coursierParallelDownloads.value
      val checksums = coursierChecksums.value
      val maxIterations = coursierMaxIterations.value
      val cachePolicy = coursierCachePolicy.value
      val cacheDir = coursierCache.value

      val resolvers = coursierResolvers.value

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
        withSignatures = false
      )

      val interProjectRepo = InterProjectRepository(projects)
      val repositories = Seq(globalPluginsRepo, interProjectRepo) ++ resolvers.flatMap(FromSbt.repository(_, ivyProperties))

      val files = Files(
        Seq("http://" -> new File(cacheDir, "http"), "https://" -> new File(cacheDir, "https")),
        () => ???,
        concurrentDownloadCount = parallelDownloads
      )

      val logger = createLogger()
      logger.foreach(_.init())
      val fetch = coursier.Fetch(
        repositories,
        files.fetch(checksums = checksums, logger = logger)(cachePolicy = CachePolicy.LocalOnly),
        files.fetch(checksums = checksums, logger = logger)(cachePolicy = cachePolicy)
      )

      def depsRepr = currentProject.dependencies.map { case (config, dep) =>
        s"${dep.module}:${dep.version}:$config->${dep.configuration}"
      }.sorted

      if (verbosity >= 0)
        errPrintln(s"Resolving ${currentProject.module.organization}:${currentProject.module.name}:${currentProject.version}")
      if (verbosity >= 1)
        for (depRepr <- depsRepr)
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
      }

      val classifiers =
        if (withClassifiers)
          Some(transitiveClassifiers.value)
        else
          None

      val allArtifacts =
        classifiers match {
          case None => res.artifacts
          case Some(cl) => res.classifiersArtifacts(cl)
        }

      val trDepsWithArtifactsTasks = allArtifacts
        .toVector
        .map { a =>
          files.file(a, checksums = checksums, logger = logger)(cachePolicy = cachePolicy).run.map((a, _))
        }

      if (verbosity >= 0)
        errPrintln(s"Fetching artifacts")
      // rename
      val trDepsWithArtifacts = Task.gatherUnordered(trDepsWithArtifactsTasks).attemptRun match {
        case -\/(ex) =>
          throw new Exception(s"Error while downloading / verifying artifacts", ex)
        case \/-(l) => l.toMap
      }
      if (verbosity >= 0)
        errPrintln(s"Fetching artifacts: done")

      val configs = ivyConfigurations.value.map(c => c.name -> c.extendsConfigs.map(_.name)).toMap
      def allExtends(c: String) = {
        // possibly bad complexity
        def helper(current: Set[String]): Set[String] = {
          val newSet = current ++ current.flatMap(configs.getOrElse(_, Nil))
          if ((newSet -- current).nonEmpty)
            helper(newSet)
          else
            newSet
        }

        helper(Set(c))
      }

      val depsByConfig = currentProject
        .dependencies
        .groupBy { case (c, _) => c }
        .map { case (c, l) =>
          c -> l.map { case (_, d) => d }
        }

      val sbtModuleReportsPerScope = configs.map { case (c, _) => c -> {
        val a = allExtends(c).flatMap(depsByConfig.getOrElse(_, Nil))
        val partialRes = res.part(a)
        val depArtifacts =
          classifiers match {
            case None => partialRes.dependencyArtifacts
            case Some(cl) => partialRes.dependencyClassifiersArtifacts(cl)
          }

        depArtifacts
          .groupBy { case (dep, _) => dep }
          .map { case (dep, l) => dep -> l.map { case (_, a) => a } }
          .map { case (dep, artifacts) =>
            val fe = artifacts.map { a =>
              a -> trDepsWithArtifacts.getOrElse(a, -\/("Not downloaded"))
            }
            new ModuleReport(
              ModuleID(dep.module.organization, dep.module.name, dep.version, configurations = Some(dep.configuration)),
              fe.collect { case (artifact, \/-(file)) =>
                if (file.toString.contains("file:/"))
                  throw new Exception(s"Wrong path: $file")
                ToSbt.artifact(dep.module, artifact) -> file
              },
              fe.collect { case (artifact, -\/(e)) =>
                errPrintln(s"${artifact.url}: $e")
                ToSbt.artifact(dep.module, artifact)
              },
              None,
              None,
              None,
              None,
              false,
              None,
              None,
              None,
              None,
              Map.empty,
              None,
              None,
              Nil,
              Nil,
              Nil
            )
          }
      }}

      new UpdateReport(
        null,
        sbtModuleReportsPerScope.toVector.map { case (c, r) =>
          new ConfigurationReport(
            c,
            r.toVector,
            Nil,
            Nil
          )
        },
        new UpdateStats(-1L, -1L, -1L, cached = false),
        Map.empty
      )
    }
  }

  override lazy val projectSettings = Seq(
    coursierParallelDownloads := 6,
    coursierMaxIterations := 50,
    coursierChecksums := Seq(Some("SHA-1"), Some("MD5"), None),
    coursierCachePolicy := CachePolicy.FetchMissing,
    coursierVerbosity := 1,
    coursierResolvers <<= Tasks.coursierResolversTask,
    coursierCache := new File(sys.props("user.home") + "/.coursier/sbt"),
    update <<= updateTask(withClassifiers = false),
    updateClassifiers <<= updateTask(withClassifiers = true),
    coursierProject <<= Tasks.coursierProjectTask,
    coursierProjects <<= Tasks.coursierProjectsTask
  )

}
