// https://github.com/sbt/sbt/issues/1710
lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    organization := "com.example",
    version := "0.1.0",
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.4",
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project")
  )

def cachedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val X1 = project.
  settings(commonSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.example" %% "y1" % "0.1.0" % "compile->compile;runtime->runtime",
      "com.example" %% "y2" % "0.1.0" % "compile->compile;runtime->runtime")
  )

lazy val Y1 = project.
  settings(commonSettings: _*).
  settings(
    name := "y1",
    libraryDependencies ++= Seq(
      // this includes slf4j 1.7.5
      "com.ning" % "async-http-client" % "1.8.14",
      // this includes slf4j 1.6.6
      "com.twitter" % "summingbird-core_2.10" % "0.5.0",
      "org.slf4j" % "slf4j-api" % "1.6.6" force()
    )
  )

lazy val Y2 = project.
  settings(commonSettings: _*).
  settings(
    name := "y2",
    libraryDependencies ++= Seq(
      // this includes slf4j 1.6.6
      "com.twitter" % "summingbird-core_2.10" % "0.5.0",
      // this includes slf4j 1.7.5
      "com.ning" % "async-http-client" % "1.8.14")
  )

lazy val root = (project in file(".")).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val x1cp = (externalDependencyClasspath in Compile in X1).value.sortBy {_.data.getName}
      // sys.error("slf4j-api is not found on X1" + x1cp)
      if (!(x1cp exists {_.data.getName contains "slf4j-api"})) {
        sys.error("slf4j-api is not found on X1" + x1cp)
      }
    }
  )
