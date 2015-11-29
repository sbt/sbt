package coursier
package cli

import java.io.{ OutputStreamWriter, File }
import java.util.UUID

import scalaz.{ \/-, -\/ }
import scalaz.concurrent.Task

object Helper {
  def validate(common: CommonOptions) = {
    import common._

    if (force && offline) {
      Console.err.println("Error: --offline (-c) and --force (-f) options can't be specified at the same time.")
      sys.exit(255)
    }

    if (parallel <= 0) {
      Console.err.println(s"Error: invalid --parallel (-n) value: $parallel")
      sys.exit(255)
    }

    ???
  }

  def fileRepr(f: File) = f.toString

  def errPrintln(s: String) = Console.err.println(s)

  def mainClasses(cl: ClassLoader): Map[(String, String), String] = {
    import scala.collection.JavaConverters._

    val metaInfs = cl.getResources("META-INF/MANIFEST.MF").asScala.toVector

    val mainClasses = metaInfs.flatMap { url =>
      val attributes = new java.util.jar.Manifest(url.openStream()).getMainAttributes

      val vendor = Option(attributes.getValue("Specification-Vendor")).getOrElse("")
      val title = Option(attributes.getValue("Specification-Title")).getOrElse("")
      val mainClass = Option(attributes.getValue("Main-Class"))

      mainClass.map((vendor, title) -> _)
    }

    mainClasses.toMap
  }
}

class Helper(
  common: CommonOptions,
  remainingArgs: Seq[String]
) {
  import common._
  import Helper.errPrintln

  implicit val cachePolicy =
    if (offline)
      CachePolicy.LocalOnly
    else if (force)
      CachePolicy.ForceDownload
    else
      CachePolicy.Default

  val cache = Cache(new File(cacheOptions.cache))
  cache.init(verbose = verbose0 >= 0)

  val repositoryIds = {
    val repositoryIds0 = repository
      .flatMap(_.split(','))
      .map(_.trim)
      .filter(_.nonEmpty)

    if (repositoryIds0.isEmpty)
      cache.default()
    else
      repositoryIds0
  }

  val repoMap = cache.map()
  val repoByBase = repoMap.map { case (_, v @ (m, _)) =>
    m.root -> v
  }

  val repositoryIdsOpt0 = repositoryIds.map { id =>
    repoMap.get(id) match {
      case Some(v) => Right(v)
      case None =>
        if (id.contains("://")) {
          val root0 = if (id.endsWith("/")) id else id + "/"
          Right(
            repoByBase.getOrElse(root0, {
              val id0 = UUID.randomUUID().toString
              if (verbose0 >= 1)
                Console.err.println(s"Addding repository $id0 ($root0)")

              // FIXME This could be done more cleanly
              cache.add(id0, root0, ivyLike = false)
              cache.map().getOrElse(id0,
                sys.error(s"Adding repository $id0 ($root0)")
              )
            })
          )
        } else
          Left(id)
    }
  }

  val notFoundRepositoryIds = repositoryIdsOpt0.collect {
    case Left(id) => id
  }

  if (notFoundRepositoryIds.nonEmpty) {
    errPrintln(
      (if (notFoundRepositoryIds.lengthCompare(1) == 0) "Repository" else "Repositories") +
        " not found: " +
        notFoundRepositoryIds.mkString(", ")
    )

    sys.exit(255)
  }

  val files = cache.files().copy(concurrentDownloadCount = parallel)

  val (repositories, fileCaches) = repositoryIdsOpt0
    .collect { case Right(v) => v }
    .unzip

  val (rawDependencies, extraArgs) = {
    val idxOpt = Some(remainingArgs.indexOf("--")).filter(_ >= 0)
    idxOpt.fold((remainingArgs, Seq.empty[String])) { idx =>
      val (l, r) = remainingArgs.splitAt(idx)
      assert(r.nonEmpty)
      (l, r.tail)
    }
  }

  val (splitDependencies, malformed) = rawDependencies.toList
    .map(_.split(":", 3).toSeq)
    .partition(_.length == 3)

  val (splitForceVersions, malformedForceVersions) = forceVersion
    .map(_.split(":", 3).toSeq)
    .partition(_.length == 3)

  if (splitDependencies.isEmpty) {
    Console.err.println(s"Error: no dependencies specified.")
    // CaseApp.printUsage[Coursier]()
    sys exit 1
  }

  if (malformed.nonEmpty || malformedForceVersions.nonEmpty) {
    if (malformed.nonEmpty) {
      errPrintln("Malformed dependency(ies), should be like org:name:version")
      for (s <- malformed)
        errPrintln(s" ${s.mkString(":")}")
    }

    if (malformedForceVersions.nonEmpty) {
      errPrintln("Malformed force version(s), should be like org:name:forcedVersion")
      for (s <- malformedForceVersions)
        errPrintln(s" ${s.mkString(":")}")
    }

    sys.exit(1)
  }

  val moduleVersions = splitDependencies.map{
    case Seq(org, name, version) =>
      (Module(org, name), version)
  }

  val deps = moduleVersions.map{case (mod, ver) =>
    Dependency(mod, ver, scope = Scope.Runtime)
  }

  val forceVersions = {
    val forceVersions0 = splitForceVersions.map {
      case Seq(org, name, version) => (Module(org, name), version)
    }

    val grouped = forceVersions0
      .groupBy { case (mod, _) => mod }
      .map { case (mod, l) => mod -> l.map { case (_, version) => version } }

    for ((mod, forcedVersions) <- grouped if forcedVersions.distinct.lengthCompare(1) > 0)
      errPrintln(s"Warning: version of $mod forced several times, using only the last one (${forcedVersions.last})")

    grouped.map { case (mod, versions) => mod -> versions.last }
  }

  val startRes = Resolution(
    deps.toSet,
    forceVersions = forceVersions,
    filter = Some(dep => keepOptional || !dep.optional)
  )

  val logger =
    if (verbose0 >= 0)
      Some(new TermDisplay(new OutputStreamWriter(System.err)))
    else
      None
  logger.foreach(_.init())
  val fetchQuiet = coursier.Fetch(
    repositories,
    files.fetch(logger = logger)(cachePolicy = CachePolicy.LocalOnly), // local files get the priority
    files.fetch(logger = logger)
  )
  val fetch0 =
    if (verbose0 <= 0) fetchQuiet
    else {
      modVers: Seq[(Module, String)] =>
        val print = Task{
          errPrintln(s"Getting ${modVers.length} project definition(s)")
        }

        print.flatMap(_ => fetchQuiet(modVers))
    }

  if (verbose0 >= 0) {
    errPrintln("Dependencies:")
    for ((mod, ver) <- moduleVersions)
      errPrintln(s"  $mod:$ver")

    if (forceVersions.nonEmpty) {
      errPrintln("Force versions:")
      for ((mod, ver) <- forceVersions.toVector.sortBy { case (mod, _) => mod.toString })
        errPrintln(s"  $mod:$ver")
    }
  }


  val res = startRes
    .process
    .run(fetch0, maxIterations)
    .run

  logger.foreach(_.stop())

  if (!res.isDone) {
    errPrintln(s"Maximum number of iteration reached!")
    sys.exit(1)
  }

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

  val trDeps = res
    .minDependencies
    .toList
    .sortBy(repr)

  if (verbose0 >= 1) {
    println("")
    println(
      trDeps
        .map(repr)
        .distinct
        .mkString("\n")
    )
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

  def fetch(main: Boolean, sources: Boolean, javadoc: Boolean): Seq[File] = {
    if (verbose0 >= 0)
      errPrintln("Fetching artifacts")
    val artifacts0 = res.artifacts
    val main0 = main || (!sources && !javadoc)
    val artifacts = artifacts0.flatMap{ artifact =>
      var l = List.empty[Artifact]
      if (sources)
        l = artifact.extra.get("sources").toList ::: l
      if (javadoc)
        l = artifact.extra.get("javadoc").toList ::: l
      if (main0)
        l = artifact :: l

      l
    }

    val logger =
      if (verbose0 >= 0)
        Some(new TermDisplay(new OutputStreamWriter(System.err)))
      else
        None
    logger.foreach(_.init())
    val tasks = artifacts.map(artifact => files.file(artifact, logger = logger).run.map(artifact.->))
    def printTask = Task {
      if (verbose0 >= 1 && artifacts.nonEmpty)
        println(s"Found ${artifacts.length} artifacts")
    }
    val task = printTask.flatMap(_ => Task.gatherUnordered(tasks))

    val results = task.run
    val errors = results.collect{case (artifact, -\/(err)) => artifact -> err }
    val files0 = results.collect{case (artifact, \/-(f)) => f }

    logger.foreach(_.stop())

    if (errors.nonEmpty) {
      println(s"${errors.size} error(s):")
      for ((artifact, error) <- errors) {
        println(s"  ${artifact.url}: $error")
      }
    }

    files0
  }
}
