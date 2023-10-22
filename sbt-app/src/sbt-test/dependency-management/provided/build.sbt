import complete.DefaultParsers._

val provided = SettingKey[Boolean]("provided")
val check = InputKey[Unit]("check")

lazy val root = (project in file(".")).
  settings(
    provided := (baseDirectory.value / "useProvided").exists,
    configuration := (if (provided.value) Provided else Compile),
    libraryDependencies += "javax.servlet" % "servlet-api" % "2.5" % configuration.value.name,
    Provided / managedClasspath := Classpaths.managedJars(Provided, classpathTypes.value, update.value),
    check := {
      val result = (
        Space ~> token(Compile.name.id | Runtime.name | Provided.name | Test.name) ~ token(Space ~> Bool)
      ).parsed
      val (conf, expected) = result
      val cp = conf match {
        case Compile.name  => (Compile / fullClasspath).value
        case Runtime.name  => (Runtime / fullClasspath).value
        case Provided.name => (Provided / managedClasspath).value
        case Test.name     => (Test / fullClasspath).value
        case _             => sys.error(s"Invalid config: $conf")
      }
      checkServletAPI(cp.files, expected, conf)
    }
  )

def checkServletAPI(paths: Seq[File], shouldBeIncluded: Boolean, label: String) = {
  val servletAPI = paths.find(_.getName contains "servlet-api")
  if (shouldBeIncluded) {
    if (servletAPI.isEmpty) sys.error(s"Servlet API should have been included in $label.")
  } else
    servletAPI foreach (s => sys.error(s"$s incorrectly included in $label."))
}
