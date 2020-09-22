ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.9.1"

name := "whatDependsOn"

resolvers += "typesafe maven" at "https://repo.typesafe.com/typesafe/maven-releases/"

libraryDependencies ++= Seq(
  "com.codahale" % "jerkson_2.9.1" % "0.5.0",
  "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.10" // as another version of asl
)

val check = TaskKey[Unit]("check")

check := {
  def sanitize(str: String): String = str.split('\n').map(_.trim).mkString("\n")
  def checkOutput(output: String, expected: String): Unit =
    require(sanitize(expected) == sanitize(output), s"Tree should have been [\n${sanitize(expected)}\n] but was [\n${sanitize(output)}\n]")

  val withVersion =
    (whatDependsOn in Compile)
      .toTask(" org.codehaus.jackson jackson-core-asl 1.9.10")
      .value
  val expectedGraphWithVersion =
    """org.codehaus.jackson:jackson-core-asl:1.9.10
      |  +-com.codahale:jerkson_2.9.1:0.5.0 [S]
      |  | +-whatdependson:whatdependson_2.9.1:0.1.0-SNAPSHOT [S]
      |  |
      |  +-org.codehaus.jackson:jackson-mapper-asl:1.9.10
      |    +-com.codahale:jerkson_2.9.1:0.5.0 [S]
      |    | +-whatdependson:whatdependson_2.9.1:0.1.0-SNAPSHOT [S]
      |    |
      |    +-whatdependson:whatdependson_2.9.1:0.1.0-SNAPSHOT [S]
      |  """.stripMargin

  checkOutput(withVersion, expectedGraphWithVersion)

  val withoutVersion =
    (whatDependsOn in Compile)
      .toTask(" org.codehaus.jackson jackson-mapper-asl")
      .value
  val expectedGraphWithoutVersion =
    """org.codehaus.jackson:jackson-mapper-asl:1.9.10
      | +-com.codahale:jerkson_2.9.1:0.5.0 [S]
      | | +-whatdependson:whatdependson_2.9.1:0.1.0-SNAPSHOT [S]
      | |
      | +-whatdependson:whatdependson_2.9.1:0.1.0-SNAPSHOT [S]
      |
      |org.codehaus.jackson:jackson-mapper-asl:[1.9.0,2.0.0) (evicted by: 1.9.10)
      | +-com.codahale:jerkson_2.9.1:0.5.0 [S]
      | +-whatdependson:whatdependson_2.9.1:0.1.0-SNAPSHOT [S]
      |   """.stripMargin
  checkOutput(withoutVersion, expectedGraphWithoutVersion)
}
