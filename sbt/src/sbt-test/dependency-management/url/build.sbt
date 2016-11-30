import sbt.internal.inc.classpath.ClasspathUtilities

lazy val root = (project in file(".")).
  settings(
    ivyPaths := (baseDirectory, target)( (dir, t) => IvyPaths(dir, Some(t / "ivy-cache"))).value,
    libraryDependencies += "org.jsoup" % "jsoup" % "1.9.1" % Test from "http://jsoup.org/packages/jsoup-1.9.1.jar",
    ivyLoggingLevel := UpdateLogging.Full,
    TaskKey[Unit]("checkInTest") := checkClasspath(Test).value,
    TaskKey[Unit]("checkInCompile") := checkClasspath(Compile).value
  )

def checkClasspath(conf: Configuration) =
  fullClasspath in conf map { cp =>
    try
    {
      val loader = ClasspathUtilities.toLoader(cp.files)
      Class.forName("org.jsoup.Jsoup", false, loader)
      ()
    }
    catch
    {
      case _: ClassNotFoundException => sys.error("Dependency not downloaded.")
    }
  }
