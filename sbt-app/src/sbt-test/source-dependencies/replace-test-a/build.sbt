import java.net.URLClassLoader

lazy val root = (project in file(".")).
  settings(
    TaskKey[Unit]("checkFirst") := checkTask("First").value,
    TaskKey[Unit]("checkSecond") := checkTask("Second").value
  )

def checkTask(className: String) =
  (Configurations.Runtime / fullClasspath) map { runClasspath =>
    val cp = runClasspath.map(_.data.toURI.toURL).toArray
    Class.forName(className, false, new URLClassLoader(cp))
    ()
  }
