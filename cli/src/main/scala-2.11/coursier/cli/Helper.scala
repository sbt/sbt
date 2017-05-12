package coursier
package cli

import java.io.{ OutputStreamWriter, File }
import java.net.{ URL, URLClassLoader }
import java.util.jar.{ Manifest => JManifest }
import java.util.concurrent.Executors

import coursier.cli.scaladex.Scaladex
import coursier.extra.Typelevel
import coursier.ivy.IvyRepository
import coursier.util.{Print, Parse}

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.Try

import scalaz.{Failure, Nondeterminism, Success, \/-, -\/}
import scalaz.concurrent.{ Task, Strategy }
import scalaz.std.list._

object Helper {
  def fileRepr(f: File) = f.toString

  def errPrintln(s: String) = Console.err.println(s)

  private val manifestPath = "META-INF/MANIFEST.MF"

  def mainClasses(cl: ClassLoader): Map[(String, String), String] = {
    import scala.collection.JavaConverters._

    val parentMetaInfs = Option(cl.getParent).fold(Set.empty[URL]) { parent =>
      parent.getResources(manifestPath).asScala.toSet
    }
    val allMetaInfs = cl.getResources(manifestPath).asScala.toVector

    val metaInfs = allMetaInfs.filterNot(parentMetaInfs)

    val mainClasses = metaInfs.flatMap { url =>
      val attributes = new JManifest(url.openStream()).getMainAttributes

      def attributeOpt(name: String) =
        Option(attributes.getValue(name))

      val vendor = attributeOpt("Specification-Vendor").getOrElse("")
      val title = attributeOpt("Specification-Title").getOrElse("")
      val mainClass = attributeOpt("Main-Class")

      mainClass.map((vendor, title) -> _)
    }

    mainClasses.toMap
  }
}

object Util {

  def prematureExit(msg: String): Nothing = {
    Console.err.println(msg)
    sys.exit(255)
  }

  def prematureExitIf(cond: Boolean)(msg: => String): Unit =
    if (cond)
      prematureExit(msg)

  def exit(msg: String): Nothing = {
    Console.err.println(msg)
    sys.exit(1)
  }

  def exitIf(cond: Boolean)(msg: => String): Unit =
    if (cond)
      exit(msg)

}

class Helper(
  common: CommonOptions,
  rawDependencies: Seq[String],
  extraJars: Seq[File] = Nil,
  printResultStdout: Boolean = false,
  ignoreErrors: Boolean = false,
  isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  warnBaseLoaderNotFound: Boolean = true
) {
  import common._
  import Helper.errPrintln

  import Util._

  val ttl0 =
    if (ttl.isEmpty)
      Cache.defaultTtl
    else
      try Some(Duration(ttl))
      catch {
        case e: Exception =>
          prematureExit(s"Unrecognized TTL duration: $ttl")
      }

  val cachePolicies =
    if (common.mode.isEmpty)
      CachePolicy.default
    else
      CacheParse.cachePolicies(common.mode) match {
        case Success(cp) => cp
        case Failure(errors) =>
          prematureExit(
            s"Error parsing modes:\n${errors.list.toList.map("  "+_).mkString("\n")}"
          )
      }

  val cache = new File(cacheOptions.cache)

  val pool = Executors.newFixedThreadPool(parallel, Strategy.DefaultDaemonThreadFactory)

  val defaultRepositories = Seq(
    Cache.ivy2Local,
    MavenRepository("https://repo1.maven.org/maven2")
  )

  val sourceDirectories = common.sources.map { path =>
    val subDir = "target/repository"
    val dir = new File(path)
    val repoDir = new File(dir, subDir)
    if (!dir.exists())
      Console.err.println(s"Warning: sources $path not found")
    else if (!repoDir.exists())
      Console.err.println(s"Warning: directory $subDir not found under sources path $path")

    repoDir
  }

  val repositoriesValidation = CacheParse.repositories(common.repository).map { repos0 =>

    var repos = (if (common.noDefault) Nil else defaultRepositories) ++ repos0

    repos = repos.map {
      case m: MavenRepository => m.copy(sbtAttrStub = common.sbtPluginHack)
      case other => other
    }

    if (common.dropInfoAttr)
      repos = repos.map {
        case m: IvyRepository => m.copy(dropInfoAttributes = true)
        case other => other
      }

    repos
  }

  val repositories = repositoriesValidation match {
    case Success(repos) =>
      val sourceRepositories = sourceDirectories.map(dir =>
        MavenRepository(dir.toURI.toString, changing = Some(true))
      )
      sourceRepositories ++ repos
    case Failure(errors) =>
      prematureExit(
        s"Error with repositories:\n${errors.list.toList.map("  "+_).mkString("\n")}"
      )
  }


  val loggerFallbackMode =
    !progress && TermDisplay.defaultFallbackMode

  val (scaladexRawDependencies, otherRawDependencies) =
    rawDependencies.partition(s => s.contains("/") || !s.contains(":"))

  val scaladexModuleVersionConfigs =
    if (scaladexRawDependencies.isEmpty)
      Nil
    else {
      val logger =
        if (verbosityLevel >= 0)
          Some(new TermDisplay(
            new OutputStreamWriter(System.err),
            fallbackMode = loggerFallbackMode
          ))
        else
          None

      val fetchs = cachePolicies.map(p =>
        Cache.fetch(cache, p, checksums = Nil, logger = logger, pool = pool, ttl = ttl0)
      )

      logger.foreach(_.init())

      val scaladex = Scaladex.cached(fetchs: _*)

      val res = Nondeterminism[Task].gather(scaladexRawDependencies.map { s =>
        val deps = scaladex.dependencies(
          s,
          scalaVersion,
          if (verbosityLevel >= 2) Console.err.println(_) else _ => ()
        )

        deps.map { modVers =>
          val m = modVers.groupBy(_._2)
          if (m.size > 1) {
            val (keptVer, modVers0) = m.map {
              case (v, l) =>
                val ver = coursier.core.Parse.version(v)
                  .getOrElse(???) // FIXME

              ver -> l
            }
            .maxBy(_._1)

            if (verbosityLevel >= 1)
              Console.err.println(s"Keeping version ${keptVer.repr}")

            modVers0
          } else
            modVers
        }.run
      }).unsafePerformSync

      logger.foreach(_.stop())

      val errors = res.collect { case -\/(err) => err }

      prematureExitIf(errors.nonEmpty) {
        s"Error getting scaladex infos:\n" + errors.map("  " + _).mkString("\n")
      }

      res
        .collect { case \/-(l) => l }
        .flatten
        .map { case (mod, ver) => (mod, ver, None) }
    }


  val (modVerCfgErrors, moduleVersionConfigs) =
    Parse.moduleVersionConfigs(otherRawDependencies, scalaVersion)
  val (intransitiveModVerCfgErrors, intransitiveModuleVersionConfigs) =
    Parse.moduleVersionConfigs(intransitive, scalaVersion)

  def allModuleVersionConfigs =
    // FIXME Order of the dependencies is not respected here (scaladex ones go first)
    scaladexModuleVersionConfigs ++ moduleVersionConfigs

  prematureExitIf(modVerCfgErrors.nonEmpty) {
    s"Cannot parse dependencies:\n" + modVerCfgErrors.map("  "+_).mkString("\n")
  }

  prematureExitIf(intransitiveModVerCfgErrors.nonEmpty) {
    s"Cannot parse intransitive dependencies:\n" +
      intransitiveModVerCfgErrors.map("  "+_).mkString("\n")
  }


  val (forceVersionErrors, forceVersions0) = Parse.moduleVersions(forceVersion, scalaVersion)

  prematureExitIf(forceVersionErrors.nonEmpty) {
    s"Cannot parse forced versions:\n" + forceVersionErrors.map("  "+_).mkString("\n")
  }

  val sourceRepositoryForceVersions = sourceDirectories.flatMap { base =>

    // FIXME Also done in the plugin module

    def pomDirComponents(f: File, components: Vector[String]): Stream[Vector[String]] =
      if (f.isDirectory) {
        val components0 = components :+ f.getName
        Option(f.listFiles()).toStream.flatten.flatMap(pomDirComponents(_, components0))
      } else if (f.getName.endsWith(".pom"))
        Stream(components)
      else
        Stream.empty

    Option(base.listFiles())
      .toVector
      .flatten
      .flatMap(pomDirComponents(_, Vector()))
      // at least 3 for org / name / version - the contrary should not happen, but who knows
      .filter(_.length >= 3)
      .map { components =>
        val org = components.dropRight(2).mkString(".")
        val name = components(components.length - 2)
        val version = components.last

        Module(org, name) -> version
      }
  }

  val forceVersions = {
    val grouped = (forceVersions0 ++ sourceRepositoryForceVersions)
      .groupBy { case (mod, _) => mod }
      .map { case (mod, l) => mod -> l.map { case (_, version) => version } }

    for ((mod, forcedVersions) <- grouped if forcedVersions.distinct.lengthCompare(1) > 0)
      errPrintln(s"Warning: version of $mod forced several times, using only the last one (${forcedVersions.last})")

    grouped.map { case (mod, versions) => mod -> versions.last }
  }

  val (excludeErrors, excludes0) = Parse.modules(exclude, scalaVersion)

  prematureExitIf(excludeErrors.nonEmpty) {
    s"Cannot parse excluded modules:\n" +
    excludeErrors
      .map("  " + _)
      .mkString("\n")
  }

  val (excludesNoAttr, excludesWithAttr) = excludes0.partition(_.attributes.isEmpty)

  prematureExitIf(excludesWithAttr.nonEmpty) {
    s"Excluded modules with attributes not supported:\n" +
    excludesWithAttr
      .map("  " + _)
      .mkString("\n")
  }

  val excludes = excludesNoAttr.map { mod =>
    (mod.organization, mod.name)
  }.toSet

  val baseDependencies = allModuleVersionConfigs.map {
    case (module, version, configOpt) =>
      Dependency(
        module,
        version,
        attributes = Attributes("", ""),
        configuration = configOpt.getOrElse(defaultConfiguration),
        exclusions = excludes
      )
  }

  val intransitiveDependencies = intransitiveModuleVersionConfigs.map {
    case (module, version, configOpt) =>
      Dependency(
        module,
        version,
        attributes = Attributes("", ""),
        configuration = configOpt.getOrElse(defaultConfiguration),
        exclusions = excludes,
        transitive = false
      )
  }

  val dependencies = baseDependencies ++ intransitiveDependencies

  val checksums = {
    val splitChecksumArgs = checksum.flatMap(_.split(',')).filter(_.nonEmpty)
    if (splitChecksumArgs.isEmpty)
      Cache.defaultChecksums
    else
      splitChecksumArgs.map {
        case none if none.toLowerCase == "none" => None
        case sumType => Some(sumType)
      }
  }

  val userEnabledProfiles = profile.toSet

  val startRes = Resolution(
    dependencies.toSet,
    forceVersions = forceVersions,
    filter = Some(dep => keepOptional || !dep.optional),
    userActivations =
      if (userEnabledProfiles.isEmpty) None
      else Some(userEnabledProfiles.iterator.map(_ -> true).toMap),
    mapDependencies = if (typelevel) Some(Typelevel.swap(_)) else None
  )

  val logger =
    if (verbosityLevel >= 0)
      Some(new TermDisplay(
        new OutputStreamWriter(System.err),
        fallbackMode = loggerFallbackMode
      ))
    else
      None

  val fetchs = cachePolicies.map(p =>
    Cache.fetch(cache, p, checksums = checksums, logger = logger, pool = pool, ttl = ttl0)
  )
  val fetchQuiet = coursier.Fetch.from(
    repositories,
    fetchs.head,
    fetchs.tail: _*
  )
  val fetch0 =
    if (verbosityLevel >= 2) {
      modVers: Seq[(Module, String)] =>
        val print = Task {
          errPrintln(s"Getting ${modVers.length} project definition(s)")
        }

        print.flatMap(_ => fetchQuiet(modVers))
    } else
      fetchQuiet

  if (verbosityLevel >= 1) {
    errPrintln(
      s"  Dependencies:\n" +
        Print.dependenciesUnknownConfigs(
          dependencies,
          Map.empty,
          printExclusions = verbosityLevel >= 2
        )
    )

    if (forceVersions.nonEmpty) {
      errPrintln("  Force versions:")
      for ((mod, ver) <- forceVersions.toVector.sortBy { case (mod, _) => mod.toString })
        errPrintln(s"$mod:$ver")
    }
  }

  logger.foreach(_.init())

  val res =
    if (benchmark > 0) {
      class Counter(var value: Int = 0) {
        def add(value: Int): Unit = {
          this.value += value
        }
      }

      def timed[T](name: String, counter: Counter, f: Task[T]): Task[T] =
        Task(System.currentTimeMillis()).flatMap { start =>
          f.map { t =>
            val end = System.currentTimeMillis()
            Console.err.println(s"$name: ${end - start} ms")
            counter.add((end - start).toInt)
            t
          }
        }

      def helper(proc: ResolutionProcess, counter: Counter, iteration: Int): Task[Resolution] =
        if (iteration >= maxIterations)
          Task.now(proc.current)
        else
          proc match {
            case _: core.Done =>
              Task.now(proc.current)
            case _ =>
              val iterationType = proc match {
                case _: core.Missing  => "IO"
                case _: core.Continue => "calculations"
                case _ => ???
              }

              timed(
                s"Iteration ${iteration + 1} ($iterationType)",
                counter,
                proc.next(fetch0, fastForward = false)).flatMap(helper(_, counter, iteration + 1)
              )
          }

      def res = {
        val iterationCounter = new Counter
        val resolutionCounter = new Counter

        val res0 = timed(
          "Resolution",
          resolutionCounter,
          helper(
            startRes.process,
            iterationCounter,
            0
          )
        ).unsafePerformSync

        Console.err.println(s"Overhead: ${resolutionCounter.value - iterationCounter.value} ms")

        res0
      }

      @tailrec
      def result(warmUp: Int): Resolution =
        if (warmUp >= benchmark) {
          Console.err.println("Benchmark resolution")
          res
        } else {
          Console.err.println(s"Warm-up ${warmUp + 1} / $benchmark")
          res
          result(warmUp + 1)
        }

      result(0)
    } else if (benchmark < 0) {

      def res(index: Int) = {
        val start = System.currentTimeMillis()
        val res0 = startRes
          .process
          .run(fetch0, maxIterations)
          .unsafePerformSync
        val end = System.currentTimeMillis()

        Console.err.println(s"Resolution ${index + 1} / ${-benchmark}: ${end - start} ms")

        res0
      }

      @tailrec
      def result(warmUp: Int): Resolution =
        if (warmUp >= -benchmark) {
          Console.err.println("Benchmark resolution")
          res(warmUp)
        } else {
          Console.err.println(s"Warm-up ${warmUp + 1} / ${-benchmark}")
          res(warmUp)
          result(warmUp + 1)
        }

      result(0)
    } else
      startRes
        .process
        .run(fetch0, maxIterations)
        .unsafePerformSync

  logger.foreach(_.stop())

  val trDeps = res.minDependencies.toVector

  lazy val projCache = res.projectCache.mapValues { case (_, p) => p }

  if (printResultStdout || verbosityLevel >= 1 || tree || reverseTree) {
    if ((printResultStdout && verbosityLevel >= 1) || verbosityLevel >= 2 || tree || reverseTree)
      errPrintln(s"  Result:")

    val depsStr =
      if (reverseTree || tree)
        Print.dependencyTree(
          dependencies,
          res,
          printExclusions = verbosityLevel >= 1,
          reverse = reverseTree
        )
      else
        Print.dependenciesUnknownConfigs(
          trDeps,
          projCache,
          printExclusions = verbosityLevel >= 1
        )

    if (printResultStdout)
      println(depsStr)
    else
      errPrintln(depsStr)
  }

  var anyError = false

  if (!res.isDone) {
    anyError = true
    errPrintln("\nMaximum number of iterations reached!")
  }

  if (res.metadataErrors.nonEmpty) {
    anyError = true
    errPrintln(
      "\nError:\n" +
      res.metadataErrors.map {
        case ((module, version), errors) =>
          s"  $module:$version\n${errors.map("    " + _.replace("\n", "    \n")).mkString("\n")}"
      }.mkString("\n")
    )
  }

  if (res.conflicts.nonEmpty) {
    anyError = true
    errPrintln(
      s"\nConflict:\n" +
      Print.dependenciesUnknownConfigs(
        res.conflicts.toVector,
        projCache,
        printExclusions = verbosityLevel >= 1
      )
    )
  }

  if (anyError) {
    if (ignoreErrors)
      errPrintln("Ignoring errors")
    else
      sys.exit(1)
  }

  def artifacts(
    sources: Boolean,
    javadoc: Boolean,
    artifactTypes: Set[String],
    subset: Set[Dependency] = null
  ): Seq[Artifact] = {

    if (subset == null && verbosityLevel >= 1) {
      def isLocal(p: CachePolicy) = p match {
        case CachePolicy.LocalOnly => true
        case CachePolicy.LocalUpdate => true
        case CachePolicy.LocalUpdateChanging => true
        case _ => false
      }

      val msg =
        if (cachePolicies.forall(isLocal))
          "  Checking artifacts"
        else
          "  Fetching artifacts"

      errPrintln(msg)
    }

    val res0 = Option(subset).fold(res)(res.subset)

    val artifacts0 =
      if (classifier0.nonEmpty || sources || javadoc) {
        var classifiers = classifier0
        if (sources)
          classifiers = classifiers + "sources"
        if (javadoc)
          classifiers = classifiers + "javadoc"

        res0.dependencyClassifiersArtifacts(classifiers.toVector.sorted).map(_._2)
      } else
        res0.dependencyArtifacts.map(_._2)

    if (artifactTypes("*"))
      artifacts0
    else
      artifacts0.filter { artifact =>
        artifactTypes(artifact.`type`)
      }
  }

  def fetch(
    sources: Boolean,
    javadoc: Boolean,
    artifactTypes: Set[String],
    subset: Set[Dependency] = null
  ): Seq[File] = {

    val artifacts0 = artifacts(sources, javadoc, artifactTypes, subset).map { artifact =>
      artifact.copy(attributes = Attributes())
    }.distinct

    val logger =
      if (verbosityLevel >= 0)
        Some(new TermDisplay(
          new OutputStreamWriter(System.err),
          fallbackMode = loggerFallbackMode
        ))
      else
        None

    if (verbosityLevel >= 1 && artifacts0.nonEmpty)
      println(s"  Found ${artifacts0.length} artifacts")

    val tasks = artifacts0.map { artifact =>
      def file(policy: CachePolicy) = Cache.file(
        artifact,
        cache,
        policy,
        checksums = checksums,
        logger = logger,
        pool = pool,
        ttl = ttl0
      )

      (file(cachePolicies.head) /: cachePolicies.tail)(_ orElse file(_))
        .run
        .map(artifact.->)
    }

    logger.foreach(_.init())

    val task = Task.gatherUnordered(tasks)

    val results = task.unsafePerformSync
    val errors = results.collect{case (artifact, -\/(err)) => artifact -> err }
    val files0 = results.collect{case (artifact, \/-(f)) => f }

    logger.foreach(_.stop())

    exitIf(errors.nonEmpty) {
      s"  Error:\n" +
      errors.map { case (artifact, error) =>
        s"${artifact.url}: $error"
      }.mkString("\n")
    }

    files0
  }

  def contextLoader = Thread.currentThread().getContextClassLoader

  def baseLoader = {

    @tailrec
    def rootLoader(cl: ClassLoader): ClassLoader =
      Option(cl.getParent) match {
        case Some(par) => rootLoader(par)
        case None => cl
      }

    rootLoader(ClassLoader.getSystemClassLoader)
  }

  lazy val (parentLoader, filteredFiles) = {

    // FIXME That shouldn't be hard-coded this way...
    // This whole class ought to be rewritten more cleanly.
    val artifactTypes = Set("jar", "bundle")

    val files0 = fetch(
      sources = false,
      javadoc = false,
      artifactTypes = artifactTypes
    )

    if (isolated.isolated.isEmpty)
      (baseLoader, files0)
    else {

      val isolatedDeps = isolated.isolatedDeps(common.scalaVersion)

      val (isolatedLoader, filteredFiles0) = isolated.targets.foldLeft((baseLoader, files0)) {
        case ((parent, files0), target) =>

          // FIXME These were already fetched above
          val isolatedFiles = fetch(
            sources = false,
            javadoc = false,
            artifactTypes = artifactTypes,
            subset = isolatedDeps.getOrElse(target, Seq.empty).toSet
          )

          if (common.verbosityLevel >= 2) {
            Console.err.println(s"Isolated loader files:")
            for (f <- isolatedFiles.map(_.toString).sorted)
              Console.err.println(s"  $f")
          }

          val isolatedLoader = new IsolatedClassLoader(
            isolatedFiles.map(_.toURI.toURL).toArray,
            parent,
            Array(target)
          )

          val filteredFiles0 = files0.filterNot(isolatedFiles.toSet)

          (isolatedLoader, filteredFiles0)
      }

      if (common.verbosityLevel >= 2) {
        Console.err.println(s"Remaining files:")
        for (f <- filteredFiles0.map(_.toString).sorted)
          Console.err.println(s"  $f")
      }

      (isolatedLoader, filteredFiles0)
    }
  }

  lazy val loader = new URLClassLoader(
    (filteredFiles ++ extraJars).map(_.toURI.toURL).toArray,
    parentLoader
  )


  lazy val retainedMainClass = {

    val mainClasses = Helper.mainClasses(loader)

    if (common.verbosityLevel >= 2) {
      Console.err.println("Found main classes:")
      for (((vendor, title), mainClass) <- mainClasses)
        Console.err.println(s"  $mainClass (vendor: $vendor, title: $title)")
      Console.err.println("")
    }

    val mainClass =
      if (mainClasses.size == 1) {
        val (_, mainClass) = mainClasses.head
        mainClass
      } else {

        // TODO Move main class detection code to the coursier-extra module to come, add non regression tests for it
        // In particular, check the main class for scalafmt, scalafix, ammonite, ...

        // Trying to get the main class of the first artifact
        val mainClassOpt = for {
          (module, _, _) <- allModuleVersionConfigs.headOption
          mainClass <- mainClasses.collectFirst {
            case ((org, name), mainClass)
              if org == module.organization && (
                module.name == name ||
                  module.name.startsWith(name + "_") // Ignore cross version suffix
                ) =>
              mainClass
          }
        } yield mainClass

        def sameOrgOnlyMainClassOpt = for {
          (module, _, _) <- allModuleVersionConfigs.headOption
          orgMainClasses = mainClasses.collect {
            case ((org, name), mainClass)
              if org == module.organization =>
              mainClass
          }.toSet
          if orgMainClasses.size == 1
        } yield orgMainClasses.head

        mainClassOpt.orElse(sameOrgOnlyMainClassOpt).getOrElse {
          Helper.errPrintln(s"Cannot find default main class. Specify one with -M or --main.")
          sys.exit(255)
        }
      }

    mainClass
  }
}
