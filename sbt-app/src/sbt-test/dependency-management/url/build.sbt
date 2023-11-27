import sbt.internal.inc.classpath.ClasspathUtilities

ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val root = (project in file(".")).
  settings(
    localCache,
    libraryDependencies += "org.jsoup" % "jsoup" % "1.9.1" % Test from "https://jsoup.org/packages/jsoup-1.9.1.jar",
    ivyLoggingLevel := UpdateLogging.Full,
    TaskKey[Unit]("checkInTest") := checkClasspath(Test).value,
    TaskKey[Unit]("checkInCompile") := checkClasspath(Compile).value
  )

def checkClasspath(conf: Configuration) =
  (conf / fullClasspath) map { cp =>
    try {
      val loader = ClasspathUtilities.toLoader(cp.files)
      Class.forName("org.jsoup.Jsoup", false, loader)
      ()
    }
    catch {
      case _: ClassNotFoundException => sys.error(s"could not instantiate org.jsoup.Jsoup: ${cp.files}")
    }
  }
