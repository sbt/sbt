package coursier.cli.options

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

import coursier.core.ResolutionProcess

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
    maxIterations: Int = ResolutionProcess.defaultMaxIterations,
  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype-snapshots, or equivalently -r central,ivy2local,sonatype-snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,
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
  @Help("Global level exclude")
    exclude: List[String] = Nil,

  @Short("x")
  @Help("Path to the local exclusion file. " +
    "Syntax: <org:name>--<org:name>. `--` means minus. Example file content:\n\t" +
    "\tcom.twitter.penguin:korean-text--com.twitter:util-tunable-internal_2.11\n\t" +
    "\torg.apache.commons:commons-math--com.twitter.search:core-query-nodes\n\t" +
    "Behavior: If root module A excludes module X, but root module B requires X, module X will still be fetched.")
    localExcludeFile: String = "",
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

  @Help("Specify path for json output")
  @Short("j")
    jsonOutputFile: String = "",

  @Help("Swap the mainline Scala JARs by Typelevel ones")
    typelevel: Boolean = false,
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Help("Retry limit for Checksum error when fetching a file")
    retryCount: Int = 1,

  @Help("Flag that specifies if a local artifact should be cached.")
  @Short("cfa")
    cacheFileArtifacts: Boolean = false

) {
  val verbosityLevel = Tag.unwrap(verbose) - (if (quiet) 1 else 0)
  lazy val classifier0 = classifier.flatMap(_.split(',')).filter(_.nonEmpty).toSet
}

object CommonOptions {
  implicit val parser = Parser[CommonOptions]
  implicit val help = caseapp.core.help.Help[CommonOptions]
}
