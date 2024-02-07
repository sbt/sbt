import sbt.internal.inc.classpath.ClasspathUtilities

lazy val root = (project in file(".")).
  settings(
    TaskKey[Unit]("checkFirst") := checkTask("First").value,
    TaskKey[Unit]("checkSecond") := checkTask("Second").value
  )

def checkTask(className: String) =
  import sbt.TupleSyntax.*
  (Configurations.Runtime / fullClasspath, fileConverter) mapN { (runClasspath, c) =>
    given FileConverter = c
    val cp = runClasspath.files
    val loader = ClasspathUtilities.toLoader(cp.map(_.toFile()))
    Class.forName(className, false, loader)
    ()
  }
