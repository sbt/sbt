lazy val root = (project in file(".")).
  settings(
    ivyPaths := IvyPaths(baseDirectory.value, Some(target.value / "ivy-cache")),
    libraryDependencies ++= baseDirectory (libraryDeps).value,
    TaskKey[Unit]("checkForced") := check("1.2.14").value,
    TaskKey[Unit]("checkDepend") := check("1.2.13").value
  )

def libraryDeps(base: File) = {
  val slf4j = Seq("org.slf4j" % "slf4j-log4j12" % "1.1.0")  // Uses log4j 1.2.13
  if ((base / "force").exists) slf4j :+ ("log4j" % "log4j" % "1.2.14" force()) else slf4j
}

def check(ver: String) =
  (dependencyClasspath in Compile) map { jars =>
    val log4j = jars map (_.data) collect {
      case f if f.getName contains "log4j-" => f.getName
    }
    if (log4j.size != 1 || !log4j.head.contains(ver))
      sys.error("Did not download the correct jar.")
  }
