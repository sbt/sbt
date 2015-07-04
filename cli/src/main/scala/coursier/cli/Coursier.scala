package coursier
package cli

import java.io.File

import caseapp._
import coursier.core.{MavenRepository, Parse, Repository}, Repository.CachePolicy

import scalaz.concurrent.Task

case class Coursier(
  scope: List[String],
  keepOptional: Boolean,
  fetch: Boolean,
  @ExtraName("c") offline: Boolean,
  @ExtraName("v") verbose: List[Unit],
  @ExtraName("N") maxIterations: Int = 100
) extends App {

  val verbose0 = verbose.length

  val scopes0 =
    if (scope.isEmpty) List(Scope.Compile, Scope.Runtime)
    else scope.map(Parse.scope)
  val scopes = scopes0.toSet

  val centralCacheDir = new File(sys.props("user.home") + "/.coursier/cache/metadata/central")
  val centralFilesCacheDir = new File(sys.props("user.home") + "/.coursier/cache/files/central")

  def fileRepr(f: File) = f.toString

  val logger: MavenRepository.Logger with Files.Logger =
    new MavenRepository.Logger with Files.Logger {
      def println(s: String) = Console.err.println(s)

      def downloading(url: String) =
        println(s"Downloading $url")
      def downloaded(url: String, success: Boolean) =
        println(
          if (success) s"Downloaded $url"
          else s"Failed to download $url"
        )
      def readingFromCache(f: File) = {
        println(s"Reading ${fileRepr(f)} from cache")
      }
      def puttingInCache(f: File) =
        println(s"Writing ${fileRepr(f)} in cache")

      def foundLocally(f: File) =
        println(s"Found locally ${fileRepr(f)}")
      def downloadingArtifact(url: String) =
        println(s"Downloading $url")
      def downloadedArtifact(url: String, success: Boolean) =
        println(
          if (success) s"Downloaded $url"
          else s"Failed to download $url"
        )
    }

  val cachedMavenCentral = Repository.mavenCentral.copy(
    cache = Some(centralCacheDir),
    logger = if (verbose0 <= 1) None else Some(logger)
  )
  val repositories = Seq[Repository](
    cachedMavenCentral,
    Repository.ivy2Local.copy(
      logger = if (verbose0 <= 1) None else Some(logger)
    )
  )

  val (splitDependencies, malformed) = remainingArgs.toList
    .map(_.split(":", 3).toSeq)
    .partition(_.length == 3)

  if (splitDependencies.isEmpty) {
    CaseApp.printUsage[Coursier]()
    sys exit 1
  }

  if (malformed.nonEmpty) {
    Console.err.println(s"Malformed dependencies:\n${malformed.map(_.mkString(":")).mkString("\n")}")
    sys exit 1
  }

  val moduleVersions = splitDependencies.map{
    case Seq(org, name, version) =>
      (Module(org, name), version)
  }

  val deps = moduleVersions.map{case (mod, ver) =>
    Dependency(mod, ver, scope = Scope.Runtime)
  }

  val startRes = Resolution(
    deps.toSet,
    filter = Some(dep => (keepOptional || !dep.optional) && scopes(dep.scope))
  )

  val fetchQuiet = coursier.fetch(repositories)
  val fetch0 =
    if (verbose0 == 0) fetchQuiet
    else {
      modVers: Seq[(Module, String)] =>
        val print = Task{
          println(s"Getting ${modVers.length} project definition(s)")
        }

        print.flatMap(_ => fetchQuiet(modVers))
    }

  val res = startRes
    .process
    .run(fetch0, maxIterations)
    .run

  if (!res.isDone) {
    Console.err.println(s"Maximum number of iteration reached!")
    sys exit 1
  }

  def repr(dep: Dependency) = {
    // dep.version can be an interval, whereas the one from project can't
    val version = res.projectCache.get(dep.moduleVersion).map(_._2.version).getOrElse(dep.version)
    val extra =
      if (version == dep.version) ""
      else s" ($version for ${dep.version})"

    s"${dep.module.organization}:${dep.module.name}:${dep.attributes.`type`}:${Some(dep.attributes.classifier).filter(_.nonEmpty).map(_+":").mkString}$version$extra"
  }

  val trDeps = res.minDependencies.toList.sortBy(repr)

  println("\n" + trDeps.map(repr).distinct.mkString("\n"))

  if (res.conflicts.nonEmpty) {
    // Needs test
    println(s"${res.conflicts.size} conflict(s):\n  ${res.conflicts.toList.map(repr).sorted.mkString("  \n")}")
  }

  val errors = res.errors
  if (errors.nonEmpty) {
    println(s"${errors.size} error(s):")
    for ((dep, errs) <- errors) {
      println(s"  ${dep.module}:\n    ${errs.map("    " + _.replace("\n", "    \n")).mkString("\n")}")
    }
  }

  if (fetch) {
    println()

    val cachePolicy: CachePolicy =
      if (offline)
        CachePolicy.LocalOnly
      else
        CachePolicy.Default

    val artifacts = res.artifacts

    val files = new Files(
      Seq(
        cachedMavenCentral.root -> centralFilesCacheDir
      ),
      () => ???,
      if (verbose0 <= 0) None else Some(logger)
    )

    val tasks = artifacts.map(files.file(_, cachePolicy).run)
    val task = Task.gatherUnordered(tasks)

    task.run
  }
}

object Coursier extends AppOf[Coursier] {
  val parser = default
}
