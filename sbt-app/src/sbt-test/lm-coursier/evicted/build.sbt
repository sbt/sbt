
// examples adapted from https://github.com/coursier/sbt-coursier/pull/75#issuecomment-497128870

lazy val a = project
  .settings(
    scalaVersion := "2.12.8",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "1.3.1",
      "org.typelevel" %% "cats-core" % "1.5.0"
    )
)

lazy val b = project
  .settings(
    scalaVersion := "2.12.8",
    libraryDependencies ++= Seq(
      "org.slf4s" %% "slf4s-api" % "1.7.25",         // depends on org.slf4j:slf4j-api:1.7.25
      "ch.qos.logback" % "logback-classic" % "1.1.2" // depends on org.slf4j:slf4j-api:1.7.6
    )
)

lazy val c = project
  .settings(
    scalaVersion := "2.12.8",
    libraryDependencies ++= Seq(
      "org.slf4s" %% "slf4s-api" % "1.7.25",         // depends on org.slf4j:slf4j-api:1.7.25
      "ch.qos.logback" % "logback-classic" % "1.1.2" // depends on org.slf4j:slf4j-api:1.7.6
    ),
    dependencyOverrides += "org.slf4j" % "slf4j-api" % "1.7.30"
)

lazy val check = taskKey[Unit]("")

Global / check := {
  inline def doCheck(project: Project, evictionsExpected: Boolean = true): Unit = {
    val report = (project / updateFull).value
    val compileReport = report
      .configurations
      .find(_.configuration.name == "compile")
      .getOrElse {
        sys.error("compile report not found")
      }

    val foundEvictions = compileReport.details.exists(_.modules.exists(_.evicted))
    if (foundEvictions != evictionsExpected)
      compileReport.details.foreach(println)
    assert(
      foundEvictions == evictionsExpected,
      if evictionsExpected then s"no evictions in ${project.id}" else s"evictions in ${project.id}"
    )
  }

  doCheck(a)
  doCheck(b)
  doCheck(c, evictionsExpected = false)
}
