package coursier
package cli

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

import coursier.util.Parse

case class CommonOptions(
  @Help("Keep optional dependencies (Maven)")
    keepOptional: Boolean,
  @Help("Download mode (default: missing, that is fetch things missing from cache)")
  @Value("offline|update-changing|update|missing|force")
  @Short("m")
    mode: String = "",
  @Help("TTL duration (e.g. \"24 hours\")")
  @Value("duration")
  @Short("l")
    ttl: String,
  @Help("Quiet output")
  @Short("q")
    quiet: Boolean,
  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter,
  @Help("Force display of progress bars")
  @Short("P")
    progress: Boolean,
  @Help("Maximum number of resolution iterations (specify a negative value for unlimited, default: 100)")
  @Short("N")
    maxIterations: Int = 100,
  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype-snapshots, or equivalently -r central,ivy2local,sonatype-snapshots)")
  @Short("r")
    repository: List[String],
  @Help("Source repository - for multiple repositories, separate with comma and/or add this option multiple times")
  @Short("R")
    sources: List[String],
  @Help("Do not add default repositories (~/.ivy2/local, and Central)")
    noDefault: Boolean = false,
  @Help("Modify names in Maven repository paths for SBT plugins")
    sbtPluginHack: Boolean = false,
  @Help("Drop module attributes starting with 'info.' - these are sometimes used by projects built with SBT")
    dropInfoAttr: Boolean = false,
  @Help("Force module version")
  @Value("organization:name:forcedVersion")
  @Short("V")
    forceVersion: List[String],
  @Help("Exclude module")
  @Value("organization:name")
  @Short("E")
    exclude: List[String],
  @Help("Add intransitive dependencies")
    intransitive: List[String],
  @Help("Classifiers that should be fetched")
  @Value("classifier1,classifier2,...")
  @Short("C")
    classifier: List[String],
  @Help("Default configuration (default(compile) by default)")
  @Value("configuration")
  @Short("c")
    defaultConfiguration: String = "default(compile)",
  @Help("Default artifact type (make it empty to follow POM packaging - default: jar)")
  @Value("type")
  @Short("a")
    defaultArtifactType: String = "jar",
  @Help("Maximum number of parallel downloads (default: 6)")
  @Short("n")
    parallel: Int = 6,
  @Help("Checksums")
  @Value("checksum1,checksum2,... - end with none to allow for no checksum validation if none are available")
    checksum: List[String],
  @Help("Print the duration of each iteration of the resolution")
  @Short("B")
  @Value("Number of warm-up resolutions - if negative, doesn't print per iteration benchmark (less overhead)")
    benchmark: Int,
  @Help("Print dependencies as a tree")
  @Short("t")
    tree: Boolean,
  @Help("Print dependencies as an inversed tree (dependees as children)")
  @Short("T")
    reverseTree: Boolean,
  @Help("Enable profile")
  @Value("profile")
  @Short("F")
    profile: List[String],
  @Recurse
    cacheOptions: CacheOptions
) {
  val verbosityLevel = Tag.unwrap(verbose) - (if (quiet) 1 else 0)
  lazy val classifier0 = classifier.flatMap(_.split(',')).filter(_.nonEmpty)
}

case class CacheOptions(
  @Help("Cache directory (defaults to environment variable COURSIER_CACHE or ~/.coursier/cache/v1)")
  @Short("C")
    cache: String = Cache.default.toString
)

case class IsolatedLoaderOptions(
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

  lazy val isolatedModuleVersions = rawIsolated.groupBy { case (t, _) => t }.map {
    case (t, l) =>
      val (errors, modVers) = Parse.moduleVersions(l.map { case (_, d) => d })

      if (errors.nonEmpty) {
        errors.foreach(Console.err.println)
        sys.exit(255)
      }

      t -> modVers
  }

  def isolatedDeps(defaultArtifactType: String) =
    isolatedModuleVersions.map {
      case (t, l) =>
        t -> l.map {
          case (mod, ver) =>
            Dependency(
              mod,
              ver,
              configuration = "runtime",
              attributes = Attributes(defaultArtifactType, "")
            )
        }
    }

}

case class FetchOptions(
  @Help("Fetch source artifacts")
  @Short("S")
    sources: Boolean,
  @Help("Fetch javadoc artifacts")
  @Short("D")
    javadoc: Boolean,
  @Help("Print java -cp compatible output")
  @Short("p")
    classpath: Boolean,
  @Help("Fetch artifacts even if the resolution is errored")
    force: Boolean,
  @Recurse
    common: CommonOptions
)

case class LaunchOptions(
  @Short("M")
  @Short("main")
    mainClass: String,
  @Recurse
    isolated: IsolatedLoaderOptions,
  @Recurse
    common: CommonOptions
)

case class BootstrapOptions(
  @Short("M")
  @Short("main")
    mainClass: String,
  @Short("o")
    output: String = "bootstrap",
  @Short("d")
    downloadDir: String,
  @Short("f")
    force: Boolean,
  @Help("Generate a standalone launcher, with all JARs included, instead of one downloading its dependencies on startup.")
  @Short("s")
    standalone: Boolean,
  @Help("Set Java properties in the generated launcher.")
  @Value("key=value")
  @Short("D")
    property: List[String],
  @Help("Set Java command-line options in the generated launcher.")
  @Value("option")
  @Short("J")
    javaOpt: List[String],
  @Recurse
    isolated: IsolatedLoaderOptions,
  @Recurse
    common: CommonOptions
)
