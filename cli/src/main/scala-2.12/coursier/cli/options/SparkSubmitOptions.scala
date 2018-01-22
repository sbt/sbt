package coursier.cli.options

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

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

object SparkSubmitOptions {
  implicit val parser = Parser[SparkSubmitOptions]
  implicit val help = caseapp.core.help.Help[SparkSubmitOptions]
}
