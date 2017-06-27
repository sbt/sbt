lazy val root = (project in file(".")).
  settings(
    ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache")),
    libraryDependencies += baseDirectory(transitive("javax.mail" % "mail" % "1.4.1")).value,
    TaskKey[Unit]("checkTransitive") := check(true).value,
    TaskKey[Unit]("checkIntransitive") := check(false).value
  )

def transitive(dep: ModuleID)(base: File) =
  if((base / "transitive").exists) dep else dep.intransitive()

def check(transitive: Boolean) =
  (dependencyClasspath in Compile) map { downloaded =>
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
