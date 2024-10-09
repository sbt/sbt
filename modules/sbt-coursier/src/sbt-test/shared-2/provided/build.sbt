import sbt.librarymanagement.Configurations.{ CompileInternal, RuntimeInternal, TestInternal }

libraryDependencies ++= Seq(
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M5",
  "com.chuusai" %% "shapeless" % "2.3.3" % Provided,
  "javax.servlet" % "servlet-api" % "2.5" % Provided
)
scalaVersion := "2.12.11"

lazy val check = taskKey[Unit]("")

check := {

  val updateReport = update.value

  def configReport(config: Configuration) = updateReport
    .configuration(config)
    .getOrElse {
      throw new Exception(
        s"$config configuration not found in update report"
      )
    }

  def checkShapelessVersions(config: Configuration, expected: Option[String]): Unit = {

    val shapelessVersions = configReport(config)
      .modules
      .map(_.module)
      .collect {
        case m if m.organization == "com.chuusai" && m.name.startsWith("shapeless") =>
          m.revision
      }
      .toSet

    assert(
      shapelessVersions == expected.toSet,
      s"Expected shapeless versions ${expected.toSet} in $config, got $shapelessVersions"
    )
  }

  def checkServletVersions(config: Configuration, expected: Option[String]): Unit = {

    val servletVersions = configReport(config)
      .modules
      .map(_.module)
      .collect {
        case m if m.organization == "javax.servlet" && m.name.startsWith("servlet-api") =>
          m.revision
      }
      .toSet

    assert(
      servletVersions == expected.toSet,
      s"Expected servlet-api versions ${expected.toSet} in $config, got $servletVersions"
    )
  }

  checkShapelessVersions(Compile, Some("2.3.2"))
  checkShapelessVersions(CompileInternal, Some("2.3.3"))
  checkShapelessVersions(Test, Some("2.3.2"))
  checkShapelessVersions(TestInternal, Some("2.3.3"))
  checkShapelessVersions(Provided, Some("2.3.3"))
  checkShapelessVersions(Runtime, Some("2.3.2"))
  checkShapelessVersions(RuntimeInternal, Some("2.3.2"))

  checkServletVersions(Compile, None)
  checkServletVersions(CompileInternal, Some("2.5"))
  checkServletVersions(Test, None)
  checkServletVersions(TestInternal, Some("2.5"))
  checkServletVersions(Provided, Some("2.5"))
  checkServletVersions(Runtime, None)
  checkServletVersions(RuntimeInternal, None)
}
