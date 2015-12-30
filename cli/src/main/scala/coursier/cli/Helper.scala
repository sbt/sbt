package coursier
package cli

import java.io.{ OutputStreamWriter, File }
import java.util.concurrent.Executors

import coursier.ivy.IvyRepository

import scalaz.{ \/-, -\/ }
import scalaz.concurrent.{ Task, Strategy }

object Helper {
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

  val cachePolicies = mode match {
    case "offline" =>
      Seq(CachePolicy.LocalOnly)
    case "update-changing" =>
      Seq(CachePolicy.UpdateChanging)
    case "update" =>
      Seq(CachePolicy.Update)
    case "missing" =>
      Seq(CachePolicy.FetchMissing)
    case "force" =>
      Seq(CachePolicy.ForceDownload)
    case "default" =>
      Seq(CachePolicy.LocalOnly, CachePolicy.FetchMissing)
    case other =>
      errPrintln(s"Unrecognized mode: $other")
      sys.exit(255)
  }

  val caches =
    Seq(
      "http://" -> new File(new File(cacheOptions.cache), "http"),
      "https://" -> new File(new File(cacheOptions.cache), "https")
    )

  val pool = Executors.newFixedThreadPool(parallel, Strategy.DefaultDaemonThreadFactory)

  val central = MavenRepository("https://repo1.maven.org/maven2/")
  val ivy2Local = MavenRepository(
    new File(sys.props("user.home") + "/.ivy2/local/").toURI.toString,
    ivyLike = true
  )
  val defaultRepositories = Seq(
    ivy2Local,
    central
  )

  val repositories0 = common.repository.map { repo =>
    val repo0 = repo.toLowerCase
    if (repo0 == "central")
      Right(central)
    else if (repo0 == "ivy2local")
      Right(ivy2Local)
    else if (repo0.startsWith("sonatype:"))
      Right(
        MavenRepository(s"https://oss.sonatype.org/content/repositories/${repo.drop("sonatype:".length)}")
      )
    else {
      val (url, r) =
        if (repo.startsWith("ivy:")) {
          val url = repo.drop("ivy:".length)
          (url, IvyRepository(url))
        } else if (repo.startsWith("ivy-like:")) {
          val url = repo.drop("ivy-like:".length)
          (url, MavenRepository(url, ivyLike = true))
        } else {
          (repo, MavenRepository(repo))
        }

      if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:/"))
        Right(r)
      else
        Left(repo -> s"Unrecognized protocol or repository: $url")
    }
  }

  val unrecognizedRepos = repositories0.collect { case Left(e) => e }
  if (unrecognizedRepos.nonEmpty) {
    errPrintln(s"${unrecognizedRepos.length} error(s) parsing repositories:")
    for ((repo, err) <- unrecognizedRepos)
      errPrintln(s"$repo: $err")
    sys.exit(255)
  }

  val repositories1 =
    (if (common.noDefault) Nil else defaultRepositories) ++
      repositories0.collect { case Right(r) => r }

  val repositories =
    if (common.sbtPluginHack)
      repositories1.map {
        case m: MavenRepository => m.copy(sbtAttrStub = true)
        case other => other
      }
    else
      repositories1

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
    case Seq(org, namePart, version) =>
      val p = namePart.split(';')
      val name = p.head
      val splitAttributes = p.tail.map(_.split("=", 2).toSeq).toSeq
      val malformedAttributes = splitAttributes.filter(_.length != 2)
      if (malformedAttributes.nonEmpty) {
        // FIXME Get these for all dependencies at once
        Console.err.println(s"Malformed attributes in ${splitDependencies.mkString(":")}")
        // :(
        sys.exit(255)
      }
      val attributes = splitAttributes.collect {
        case Seq(k, v) => k -> v
      }
      (Module(org, name, attributes.toMap), version)
  }

  val deps = moduleVersions.map{case (mod, ver) =>
    Dependency(mod, ver, configuration = "runtime")
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

  val fetchs = cachePolicies.map(p =>
    Cache.fetch(caches, p, logger = logger, pool = pool)
  )
  val fetchQuiet = coursier.Fetch(
    repositories,
    fetchs.head,
    fetchs.tail: _*
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

  logger.foreach(_.init())

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

  def fetch(sources: Boolean, javadoc: Boolean): Seq[File] = {
    if (verbose0 >= 0) {
      val msg = cachePolicies match {
        case Seq(CachePolicy.LocalOnly) =>
          "Checking artifacts"
        case _ =>
          "Fetching artifacts"
      }

      errPrintln(msg)
    }
    val artifacts =
      if (sources || javadoc) {
        var classifiers = Seq.empty[String]
        if (sources)
          classifiers = classifiers :+ "sources"
        if (javadoc)
          classifiers = classifiers :+ "javadoc"

        res.classifiersArtifacts(classifiers)
      } else
        res.artifacts

    val logger =
      if (verbose0 >= 0)
        Some(new TermDisplay(new OutputStreamWriter(System.err)))
      else
        None

    if (verbose0 >= 1 && artifacts.nonEmpty)
      println(s"Found ${artifacts.length} artifacts")

    val tasks = artifacts.map(artifact =>
      (Cache.file(artifact, caches, cachePolicies.head, logger = logger, pool = pool) /: cachePolicies.tail)(
        _ orElse Cache.file(artifact, caches, _, logger = logger, pool = pool)
      ).run.map(artifact.->)
    )

    logger.foreach(_.init())

    val task = Task.gatherUnordered(tasks)

    logger.foreach(_.stop())

    val results = task.run
    val errors = results.collect{case (artifact, -\/(err)) => artifact -> err }
    val files0 = results.collect{case (artifact, \/-(f)) => f }

    if (errors.nonEmpty) {
      println(s"${errors.size} error(s):")
      for ((artifact, error) <- errors) {
        println(s"  ${artifact.url}: $error")
      }
    }

    files0
  }
}
