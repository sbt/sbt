
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

lazy val check = taskKey[Unit]("")

check := {

  val aReport = update.in(a).value
  val bReport = update.in(b).value

  def doCheck(report: UpdateReport): Unit = {

    val compileReport = report
      .configurations
      .find(_.configuration.name == "compile")
      .getOrElse {
        sys.error("compile report not found")
      }

    val foundEvictions = compileReport.details.exists(_.modules.exists(_.evicted))
    if (!foundEvictions)
      compileReport.details.foreach(println)
    assert(foundEvictions)
  }

  // needs https://github.com/coursier/coursier/pull/1217
  // doCheck(aReport)
  doCheck(bReport)
}
