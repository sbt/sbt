// https://github.com/sbt/sbt/issues/1710
// https://github.com/sbt/sbt/issues/1760

inThisBuild(Seq(
  organization := "com.example",
  version := "0.1.0",
  scalaVersion := "2.10.4",
  updateOptions := updateOptions.value.withCachedResolution(true)
))

def commonSettings: Seq[Def.Setting[_]] = Seq(
  ivyPaths := IvyPaths((baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
  dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
  fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
)

val x1 = project.settings(
  commonSettings,
  libraryDependencies += "com.example" %% "y1" % "0.1.0" % "compile;runtime->runtime",
  libraryDependencies += "com.example" %% "y2" % "0.1.0" % "compile;runtime->runtime"
)

val y1 = project.settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.ning"        % "async-http-client"     % "1.8.14",         // this includes slf4j 1.7.5
    "com.twitter"     % "summingbird-core_2.10" % "0.5.0",          // this includes slf4j 1.6.6
    "org.slf4j"       % "slf4j-api"             % "1.6.6" force(),
    "commons-logging" % "commons-logging"       % "1.1"             // this includes servlet-api 2.3
  )
)

val y2 = project.settings(
  commonSettings,
  libraryDependencies ++= Seq(
    "com.twitter"     % "summingbird-core_2.10" % "0.5.0",  // this includes slf4j 1.6.6
    "com.ning"        % "async-http-client"     % "1.8.14", // this includes slf4j 1.7.5
    "commons-logging" % "commons-logging"       % "1.1.3"
  )
)

TaskKey[Unit]("check") := {
  val x1cp = (externalDependencyClasspath in Compile in x1).value.map(_.data.getName).sorted
  def x1cpStr = x1cp.mkString("\n* ", "\n* ", "")

  if (!(x1cp contains "slf4j-api-1.6.6.jar"))
    sys.error(s"slf4j-api-1.6.6.jar is not found on X1:$x1cpStr")

  if (x1cp contains "servlet-api-2.3.jar")
    sys.error(s"servlet-api-2.3.jar is found when it should be evicted:$x1cpStr")
}
