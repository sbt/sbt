ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"
ThisBuild / scalaVersion := "2.12.17"

def localCache =
  ivyPaths := IvyPaths(baseDirectory.value.toString, Some(((ThisBuild / baseDirectory).value / "ivy" / "cache").toString))

lazy val root = (project in file(".")).
  settings(
    localCache,
    libraryDependencies += baseDirectory(transitive("javax.mail" % "mail" % "1.4.1")).value,
    TaskKey[Unit]("checkTransitive") := check(true).value,
    TaskKey[Unit]("checkIntransitive") := check(false).value
  )

def transitive(dep: ModuleID)(base: File) =
  if((base / "transitive").exists) dep else dep.intransitive()

def check(transitive: Boolean) =
  (Compile / dependencyClasspath) map { downloaded =>
    val jars = downloaded.size
    if(transitive) {
      if (jars <= 2)
        sys.error(s"Transitive dependencies not downloaded, found:\n * ${downloaded.mkString("\n * ")}")
      else ()
    } else {
      if (jars > 2)
        sys.error(s"Transitive dependencies not downloaded, found:\n * ${downloaded.mkString("\n * ")}")
      else ()
    }
  }
