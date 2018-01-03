// https://github.com/sbt/sbt/issues/1710
// https://github.com/sbt/sbt/issues/1760
lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    organization := "com.example",
    version := "0.1.0",
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((baseDirectory in LocalRootProject).value / "ivy-cache")),
    dependencyCacheDirectory := (baseDirectory in LocalRootProject).value / "dependency",
    scalaVersion := "2.10.7",
    fullResolvers := fullResolvers.value.filterNot(_.name == "inter-project"),
    resolvers += Resolver.sonatypeRepo("staging")
  )

def cachedResolutionSettings: Seq[Def.Setting[_]] =
  commonSettings ++ Seq(
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

lazy val X1 = project.
  settings(cachedResolutionSettings: _*).
  settings(
    libraryDependencies ++= Seq(
      "com.example" %% "y1" % "0.1.0" % "compile->compile;runtime->runtime",
      "com.example" %% "y2" % "0.1.0" % "compile->compile;runtime->runtime")
  )

lazy val Y1 = project.
  settings(cachedResolutionSettings: _*).
  settings(
    name := "y1",
    libraryDependencies ++= Seq(
      // this includes slf4j 1.7.5
      "com.ning" % "async-http-client" % "1.8.14",
      // this includes slf4j 1.6.6
      "com.twitter" % "summingbird-core_2.10" % "0.5.0",
      "org.slf4j" % "slf4j-api" % "1.6.6" force(),
      // this includes servlet-api 2.3
      "commons-logging" % "commons-logging" % "1.1"
    )
  )

lazy val Y2 = project.
  settings(cachedResolutionSettings: _*).
  settings(
    name := "y2",
    libraryDependencies ++= Seq(
      // this includes slf4j 1.6.6
      "com.twitter" % "summingbird-core_2.10" % "0.5.0",
      // this includes slf4j 1.7.5
      "com.ning" % "async-http-client" % "1.8.14",
      "commons-logging" % "commons-logging" % "1.1.3"
    )
  )

lazy val root = (project in file(".")).
  settings(
    organization in ThisBuild := "org.example",
    version in ThisBuild := "1.0",
    check := {
      val x1cp = (externalDependencyClasspath in Compile in X1).value.map {_.data.getName}.sorted
      // sys.error("slf4j-api is not found on X1" + x1cp)
      if (!(x1cp contains "slf4j-api-1.6.6.jar")) {
        sys.error("slf4j-api-1.6.6.jar is not found on X1" + x1cp)
      }

      //sys.error(x1cp.toString)
      if (x1cp contains "servlet-api-2.3.jar") {
        sys.error("servlet-api-2.3.jar is found when it should be evicted:" + x1cp)
      } 
    }
  )
