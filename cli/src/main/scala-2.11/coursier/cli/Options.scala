package coursier
package cli

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

import coursier.util.Parse

final case class CommonOptions(
  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean = false,
  @Help("Download mode (default: missing, that is fetch things missing from cache)")
  @Value("offline|update-changing|update|missing|force")
  @Short("m")
    mode: String = "",
  @Help("TTL duration (e.g. \"24 hours\")")
  @Value("duration")
  @Short("l")
    ttl: String = "",
  @Help("Quiet output")
  @Short("q")
    quiet: Boolean = false,
  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0),
  @Help("Force display of progress bars")
  @Short("P")
    progress: Boolean = false,
  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = 100,
  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype-snapshots, or equivalently -r central,ivy2local,sonatype-snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,
  @Help("Source repository - for multiple repositories, separate with comma and/or add this option multiple times")
  @Short("R")
    sources: List[String] = Nil,
  @Help("Do not add default repositories (~/.ivy2/local, and Central)")
    noDefault: Boolean = false,
  @Help("Modify names in Maven repository paths for SBT plugins")
    sbtPluginHack: Boolean = true,
  @Help("Drop module attributes starting with 'info.' - these are sometimes used by projects built with SBT")
    dropInfoAttr: Boolean = false,
  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String] = Nil,
  @Help("Exclude module")
  @Value("organization:name")
  @Short("E")
    exclude: List[String] = Nil,
  @Help("Default scala version")
  @Short("e")
    scalaVersion: String = scala.util.Properties.versionNumberString,
  @Help("Add intransitive dependencies")
    intransitive: List[String] = Nil,
  @Help("Classifiers that should be fetched")
  @Value("classifier1,classifier2,...")
  @Short("C")
    classifier: List[String] = Nil,
  @Help("Default configuration (default(compile) by default)")
  @Value("configuration")
  @Short("c")
    defaultConfiguration: String = "default(compile)",
  @Help("Maximum number of parallel downloads (default: 6)")
  @Short("n")
    parallel: Int = 6,
  @Help("Checksums")
  @Value("checksum1,checksum2,... - end with none to allow for no checksum validation if none are available")
    checksum: List[String] = Nil,
  @Help("Print the duration of each iteration of the resolution")
  @Short("B")
  @Value("Number of warm-up resolutions - if negative, doesn't print per iteration benchmark (less overhead)")
    benchmark: Int = 0,
  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean = false,
  @Help("Print dependencies as an inversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean = false,
  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String] = Nil,
  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false,
  @Recurse
    cacheOptions: CacheOptions = CacheOptions()
) {
  val verbosityLevel = Tag.unwrap(verbose) - (if (quiet) 1 else 0)
  lazy val classifier0 = classifier.flatMap(_.split(',')).filter(_.nonEmpty).toSet
}

final case class CacheOptions(
  @Help("Cache directory (defaults to environment variable COURSIER_CACHE or ~/.coursier/cache/v1)")
  @Short("C")
    cache: String = Cache.default.toString
)

final case class IsolatedLoaderOptions(
  @Value("target:dependency")
  @Short("I")
    isolated: List[String] = Nil,
  @Help("Comma-separated isolation targets")
  @Short("i")
    isolateTarget: List[String] = Nil
) {

  def anyIsolatedDep = isolateTarget.nonEmpty || isolated.nonEmpty

  lazy val targets = {
    val l = isolateTarget.flatMap(_.split(',')).filter(_.nonEmpty)
    val (invalid, valid) = l.partition(_.contains(":"))
    if (invalid.nonEmpty) {
      Console.err.println(s"Invalid target IDs:")
      for (t <- invalid)
        Console.err.println(s"  $t")
      sys.exit(255)
    }
    if (valid.isEmpty)
      Array("default")
    else
      valid.toArray
  }

  lazy val (validIsolated, unrecognizedIsolated) = isolated.partition(s => targets.exists(t => s.startsWith(t + ":")))

  def check() = {
    if (unrecognizedIsolated.nonEmpty) {
      Console.err.println(s"Unrecognized isolation targets in:")
      for (i <- unrecognizedIsolated)
        Console.err.println(s"  $i")
      sys.exit(255)
    }
  }

  lazy val rawIsolated = validIsolated.map { s =>
    val Array(target, dep) = s.split(":", 2)
    target -> dep
  }

  def isolatedModuleVersions(defaultScalaVersion: String) = rawIsolated.groupBy { case (t, _) => t }.map {
    case (t, l) =>
      val (errors, modVers) = Parse.moduleVersions(l.map { case (_, d) => d }, defaultScalaVersion)

      if (errors.nonEmpty) {
        errors.foreach(Console.err.println)
        sys.exit(255)
      }

      t -> modVers
  }

  def isolatedDeps(defaultScalaVersion: String) =
    isolatedModuleVersions(defaultScalaVersion).map {
      case (t, l) =>
        t -> l.map {
          case (mod, ver) =>
            Dependency(
              mod,
              ver,
              configuration = "runtime",
              attributes = Attributes("", "")
            )
        }
    }

}

object ArtifactOptions {
  def defaultArtifactTypes = Set("jar", "bundle")
}

final case class ArtifactOptions(
  @Help("Artifact types that should be retained (e.g. jar, src, doc, etc.) - defaults to jar,bundle")
  @Value("type1,type2,...")
  @Short("A")
    artifactType: List[String] = Nil,
  @Help("Fetch artifacts even if the resolution is errored")
    force: Boolean = false
) {
  def artifactTypes(sources: Boolean, javadoc: Boolean) = {
    val types0 = artifactType
      .flatMap(_.split(','))
      .filter(_.nonEmpty)
      .toSet

    if (types0.isEmpty) {
      if (sources || javadoc)
        Some("src").filter(_ => sources).toSet ++ Some("doc").filter(_ => javadoc)
      else
        ArtifactOptions.defaultArtifactTypes
    } else if (types0("*"))
      Set("*")
    else
      types0
  }
}

final case class FetchOptions(
  @Help("Fetch source artifacts")
  @Short("S")
    sources: Boolean = false,
  @Help("Fetch javadoc artifacts")
  @Short("D")
    javadoc: Boolean = false,
  @Help("Print java -cp compatible output")
  @Short("p")
    classpath: Boolean = false,
  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

final case class LaunchOptions(
  @Short("M")
  @Short("main")
    mainClass: String = "",
  @Short("J")
  @Help("Extra JARs to be added to the classpath of the launched application. Directories accepted too.")
    extraJars: List[String] = Nil,
  @Recurse
    isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

final case class BootstrapOptions(
  @Short("M")
  @Short("main")
    mainClass: String = "",
  @Short("o")
    output: String = "bootstrap",
  @Short("d")
    downloadDir: String = "",
  @Short("f")
    force: Boolean = false,
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Boolean = false,
  @Help("Set Java properties in the generated launcher.")
  @Value("key=value")
  @Short("D")
    property: List[String] = Nil,
  @Help("Set Java command-line options in the generated launcher.")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,
  @Recurse
    isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

final case class SparkSubmitOptions(
  @Short("M")
  @Short("main")
  @Help("Main class to be launched (optional if in manifest)")
    mainClass: String = "",
  @Short("J")
  @Help("Extra JARs to be added in the classpath of the job")
    extraJars: List[String] = Nil,
  @Help("If master is yarn-cluster, write YARN app ID to a file. (The ID is deduced from the spark-submit output.)")
  @Value("file")
    yarnIdFile: String = "",
  @Help("Generate Spark Yarn assembly (Spark 1.x) or fetch Spark Yarn jars (Spark 2.x), and supply those to Spark via conf. (Default: true)")
    autoAssembly: Boolean = true,
  @Help("Include default dependencies in Spark Yarn assembly or jars (see --auto-assembly). If --auto-assembly is false, the corresponding dependencies will still be shunted from the job classpath if this option is true. (Default: same as --auto-assembly)")
    defaultAssemblyDependencies: Option[Boolean] = None,
  assemblyDependencies: List[String] = Nil,
  sparkAssemblyDependencies: List[String] = Nil,
  noDefaultSubmitDependencies: Boolean = false,
  submitDependencies: List[String] = Nil,
  @Help("Spark version - if empty, deduced from the job classpath. (Default: empty)")
    sparkVersion: String = "",
  @Help("YARN version - only used with Spark 2. (Default: 2.7.3)")
    yarnVersion: String = "2.7.3",
  @Help("Maximum idle time of spark-submit (time with no output). Exit early if no output from spark-submit for more than this duration. Set to 0 for unlimited. (Default: 0)")
  @Value("seconds")
    maxIdleTime: Int = 0,
  @Recurse
    artifactOptions: ArtifactOptions = ArtifactOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)
