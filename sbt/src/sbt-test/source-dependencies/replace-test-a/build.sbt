import java.net.URLClassLoader

lazy val root = (project in file(".")).
  settings(
    TaskKey[Unit]("checkFirst") <<= checkTask("First"),
    TaskKey[Unit]("checkSecond") <<= checkTask("Second")
  )

def checkTask(className: String) =
  fullClasspath in Configurations.Runtime map { runClasspath =>
    val cp = runClasspath.map(_.data.toURI.toURL).toArray
    Class.forName(className, false, new URLClassLoader(cp))
    ()
  }
