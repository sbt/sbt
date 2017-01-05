lazy val check = taskKey[Unit]("Runs the check")

val sprayV = "1.1.1"
val playVersion = "2.2.0"
val summingbirdVersion = "0.4.0"
val luceneVersion = "4.0.0"
val akkaVersion = "2.3.1"

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    scalaVersion := "2.10.4",
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val a = project.
  settings(commonSettings: _*).
  settings(
    name := "a",
    libraryDependencies := Seq(
      organization.value %% "c" % version.value,
      "commons-io" % "commons-io" % "1.3",
      "org.apache.spark" %% "spark-core" % "0.9.0-incubating",
      "org.apache.avro" % "avro" % "1.7.7",
      "com.linkedin.pegasus" % "data-avro" % "1.9.40",
      "org.jboss.netty" % "netty" % "3.2.0.Final"
    )
  )

lazy val b = project.
  settings(commonSettings: _*).
  settings(
    name := "b",
    // this adds circular dependency
    libraryDependencies := Seq(organization.value %% "c" % version.value)
  )

lazy val c = project.
  settings(commonSettings: _*).
  settings(
    name := "c",
    libraryDependencies := Seq(organization.value %% "b" % version.value)
  )

lazy val root = (project in file(".")).
  settings(commonSettings: _*).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0-SNAPSHOT",
    check := {
      val acp = (externalDependencyClasspath in Compile in a).value.map {_.data.getName}.sorted
      if (!(acp contains "netty-3.2.0.Final.jar")) {
        sys.error("netty-3.2.0.Final not found when it should be included: " + acp.toString)
      }
    }
  )
