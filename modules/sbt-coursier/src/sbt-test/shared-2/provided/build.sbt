libraryDependencies ++= Seq(
  "com.github.alexarchambault" %% "argonaut-shapeless_6.2" % "1.2.0-M5",
  "com.chuusai" %% "shapeless" % "2.3.3" % Provided
)
scalaVersion := "2.12.11"

lazy val check = taskKey[Unit]("")

check := {

  val updateReport = update.value

  def checkVersions(config: Configuration): Unit = {

    val configReport = updateReport
      .configuration(Compile)
      .getOrElse {
        throw new Exception(
          s"$config configuration not found in update report"
        )
      }

    val shapelessVersions = configReport
      .modules
      .map(_.module)
      .collect {
        case m if m.organization == "com.chuusai" && m.name.startsWith("shapeless") =>
          m.revision
      }
      .toSet

    val expectedShapelessVersions = Set("2.3.3")
    assert(
      shapelessVersions == expectedShapelessVersions,
      s"Expected shapeless versions $expectedShapelessVersions, got $shapelessVersions"
    )
  }

  checkVersions(Compile)
  checkVersions(Provided)
}
