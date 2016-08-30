import complete.DefaultParsers._

val provided = SettingKey[Boolean]("provided")
val check = InputKey[Unit]("check")

lazy val root = (project in file(".")).
  settings(
    provided := baseDirectory(_ / "useProvided" exists).value,
    configuration := provided(p => if(p) Provided else Compile).value,
    libraryDependencies += configuration(c => "javax.servlet" % "servlet-api" % "2.5" % c.name).value,
    managedClasspath in Provided := ((classpathTypes, update) map { (cpts, report) => Classpaths.managedJars(Provided, cpts, report) }).value,
    check := (InputTask(_ => Space ~> token(Compile.name.id | Runtime.name | Provided.name | Test.name) ~ token(Space ~> Bool)) { result =>
      (result, managedClasspath in Provided, fullClasspath in Runtime, fullClasspath in Compile, fullClasspath in Test) map { case ((conf, expected), p, r, c, t) =>
        val cp = if(conf == Compile.name) c else if(conf == Runtime.name) r else if(conf == Provided.name) p else if(conf == Test.name) t else sys.error("Invalid config: " + conf)
        checkServletAPI(cp.files, expected, conf)
      }
    }).evaluated
  )

def checkServletAPI(paths: Seq[File], shouldBeIncluded: Boolean, label: String) =
  {
    val servletAPI = paths.find(_.getName contains "servlet-api")
    if(shouldBeIncluded)
    {
      if(servletAPI.isEmpty)
        sys.error("Servlet API should have been included in " + label + ".")
    }
    else
      servletAPI.foreach(s => sys.error(s + " incorrectly included in " + label + "."))
  }
