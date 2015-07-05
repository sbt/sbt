package coursier
package cli

import java.io.File

import caseapp._
import coursier.core.{ MavenRepository, Parse, CachePolicy }

import scalaz.{ \/-, -\/ }
import scalaz.concurrent.Task

case class Coursier(
  scope: List[String],
  keepOptional: Boolean,
  fetch: Boolean,
  @ExtraName("J") default: Boolean,
  @ExtraName("S") sources: Boolean,
  @ExtraName("D") javadoc: Boolean,
  @ExtraName("P") @ExtraName("cp") classpath: Boolean,
  @ExtraName("c") offline: Boolean,
  @ExtraName("f") force: Boolean,
  @ExtraName("q") quiet: Boolean,
  @ExtraName("v") verbose: List[Unit],
  @ExtraName("N") maxIterations: Int = 100,
  @ExtraName("r") repository: List[String],
  @ExtraName("n") parallel: Option[Int]
) extends App {

  val verbose0 = {
    verbose.length +
      (if (quiet) 1 else 0)
  }

  val scopes0 =
    if (scope.isEmpty) List(Scope.Compile, Scope.Runtime)
    else scope.map(Parse.scope)
  val scopes = scopes0.toSet

  def fileRepr(f: File) = f.toString

  def println(s: String) = Console.err.println(s)


  if (force && offline) {
    println("Error: --offline (-c) and --force (-f) options can't be specified at the same time.")
    sys.exit(255)
  }


  def defaultLogger: MavenRepository.Logger with Files.Logger =
    new MavenRepository.Logger with Files.Logger {
      def downloading(url: String) =
        println(s"Downloading $url")
      def downloaded(url: String, success: Boolean) =
        if (!success)
          println(s"Failed to download $url")
      def readingFromCache(f: File) = {}
      def puttingInCache(f: File) = {}

      def foundLocally(f: File) = {}
      def downloadingArtifact(url: String) =
        println(s"Downloading $url")
      def downloadedArtifact(url: String, success: Boolean) =
        if (!success)
          println(s"Failed to download $url")
    }

  def verboseLogger: MavenRepository.Logger with Files.Logger =
    new MavenRepository.Logger with Files.Logger {
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

  val logger =
    if (verbose0 < 0)
      None
    else if (verbose0 == 0)
      Some(defaultLogger)
    else
      Some(verboseLogger)

  implicit val cachePolicy =
    if (offline)
      CachePolicy.LocalOnly
    else if (force)
      CachePolicy.ForceDownload
    else
      CachePolicy.Default

  val cache = Cache.default

  val repositoryIds = {
    val repository0 = repository
      .flatMap(_.split(','))
      .map(_.trim)
      .filter(_.nonEmpty)

    if (repository0.isEmpty)
      cache.default()
    else
      repository0
  }

  val existingRepo = cache
    .list()
    .map(_._1)
    .toSet
  if (repositoryIds.exists(!existingRepo(_))) {
    val notFound = repositoryIds
      .filter(!existingRepo(_))

    Console.err.println(
      (if (notFound.lengthCompare(1) == 1) "Repository" else "Repositories") +
      " not found: " +
      notFound.mkString(", ")
    )

    sys.exit(1)
  }


  val (repositories0, fileCaches) = cache
    .list()
    .map{case (_, repo, cacheEntry) => (repo, cacheEntry)}
    .unzip

  val repositories = repositories0
    .map(_.copy(logger = logger))


  val (splitDependencies, malformed) = remainingArgs.toList
    .map(_.split(":", 3).toSeq)
    .partition(_.length == 3)

  if (splitDependencies.isEmpty) {
    CaseApp.printUsage[Coursier]()
    sys exit 1
  }

  if (malformed.nonEmpty) {
    println(s"Malformed dependencies:\n${malformed.map(_.mkString(":")).mkString("\n")}")
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

  if (verbose0 >= 0)
    println(s"Resolving\n" + moduleVersions.map{case (mod, ver) => s"  $mod:$ver"}.mkString("\n"))

  val res = startRes
    .process
    .run(fetch0, maxIterations)
    .run

  if (!res.isDone) {
    println(s"Maximum number of iteration reached!")
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

  if (verbose0 >= 0)
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

  if (fetch || classpath) {
    println("")

    val artifacts0 = res.artifacts
    val default0 = default || (!sources && !javadoc)
    val artifacts = artifacts0
      .flatMap{ artifact =>
        var l = List.empty[Artifact]
        if (sources)
          l = artifact.extra.get("sources").toList ::: l
        if (javadoc)
          l = artifact.extra.get("javadoc").toList ::: l
        if (default0)
          l = artifact :: l

        l
      }

    val files = {
      var files0 = cache
        .files()
        .copy(logger = logger)
      for (n <- parallel if n > 0)
        files0 = files0.copy(concurrentDownloadCount = n)
      files0
    }

    val tasks = artifacts.map(artifact => files.file(artifact, cachePolicy).run.map(artifact.->))
    def printTask = Task{
      if (verbose0 >= 0 && artifacts.nonEmpty)
        println(s"Found ${artifacts.length} artifacts")
    }
    val task = printTask.flatMap(_ => Task.gatherUnordered(tasks))

    val results = task.run
    val errors = results.collect{case (artifact, -\/(err)) => artifact -> err }
    val files0 = results.collect{case (artifact, \/-(f)) => f }

    if (errors.nonEmpty) {
      println(s"${errors.size} error(s):")
      for ((artifact, error) <- errors) {
        println(s"  ${artifact.url}: $error")
      }
    }

    Console.out.println(
      files0
        .map(_.toString)
        .mkString(if (classpath) File.pathSeparator else "\n")
    )
  }
}

object Coursier extends AppOf[Coursier] {
  val parser = default
}
