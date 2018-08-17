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
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
  )

lazy val a = project.
  settings(
    commonSettings,
    name := "a",
    libraryDependencies := Seq(
      "commons-io" % "commons-io" % "1.3",
      "org.apache.spark" %% "spark-core" % "0.9.0-incubating",
      "org.apache.avro" % "avro" % "1.7.7",
      "com.linkedin.pegasus" % "data-avro" % "1.9.40",
      "org.jboss.netty" % "netty" % "3.2.0.Final"
    )
  )

lazy val b = project.
  settings(
    commonSettings,
    name := "b"
  )

lazy val c = project.
  settings(
    commonSettings,
    name := "c",
    libraryDependencies := Seq(organization.value %% "b" % version.value)
  )

lazy val root = (project in file(".")).
  settings(commonSettings).
  settings(inThisBuild(Seq(
    organization := "org.example",
    version := "1.0-SNAPSHOT",
    updateOptions := updateOptions.value.withCachedResolution(true)
  )))
