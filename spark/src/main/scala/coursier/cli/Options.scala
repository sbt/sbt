package coursier.cli

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

case class SparkSubmitOptions(
  @Short("M")
  @Short("main")
    mainClass: String,
  @Help("If master is yarn-cluster, write YARN app ID to a file. (The ID is deduced from the spark-submit output.)")
  @Value("file")
    yarnIdFile: String,
  @Help("Spark home (default: SPARK_HOME from the environment)")
    sparkHome: String,
  @Help("Maximum idle time of spark-submit (time with no output). Exit early if no output from spark-submit for more than this duration. Set to 0 for unlimited. (Default: 0)")
  @Value("seconds")
    maxIdleTime: Int,
  @Recurse
    common: CommonOptions
)