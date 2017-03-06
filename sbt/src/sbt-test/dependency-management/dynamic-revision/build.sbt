lazy val check = taskKey[Unit]("Runs the check")

def commonSettings: Seq[Def.Setting[_]] =
  Seq(
    ivyPaths := new IvyPaths( (baseDirectory in ThisBuild).value, Some((target in LocalRootProject).value / "ivy-cache")),
    scalaVersion := "2.10.6"
  )

lazy val root = (project in file(".")).
  settings(
    commonSettings,
    libraryDependencies ++= Seq(
      "org.webjars.bower" % "angular" % "1.4.7",
      "org.webjars.bower" % "angular-bootstrap" % "0.14.2"
    ),
    resolvers += Resolver.typesafeRepo("releases"),
    check := {
      val acp = (externalDependencyClasspath in Compile).value.map {_.data.getName}.sorted
      if (!(acp contains "angular-1.4.7.jar")) {
        sys.error("angular-1.4.7.jar not found when it should be included: " + acp.toString)
      }
    }
  )
