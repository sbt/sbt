import sbt.classpath.ClasspathUtilities

lazy val superRoot = (project in file("super")).
  dependsOn(rootRef)

lazy val root = (project in file(".")).
  dependsOn(sub % "provided->test").
  settings(
    rootSettings
  )

lazy val sub = project

lazy val rootRef = LocalProject("root")

def rootSettings = (TaskKey[Unit]("check") := checkTask.value)
def checkTask = (fullClasspath in (rootRef, Compile), fullClasspath in (rootRef, Runtime), fullClasspath in (rootRef, Test), fullClasspath in (sub, Test), fullClasspath in (superRoot, Compile)) map { (rc, rr, rt, st, pr) =>
  check0(st, "sub test", true)
  check0(pr, "superRoot main", false)
  check0(rc, "root main", true)
  check0(rr, "root runtime", false)
  check0(rt, "root test", true)
}

def check0(cp: Seq[Attributed[File]], label: String, shouldSucceed: Boolean): Unit =
{
  val loader = ClasspathUtilities.toLoader(cp.files)
  println("Checking " + label)
  val err = try { Class.forName("org.example.ProvidedTest", false, loader); None }
  catch { case e: Exception => Some(e) }

  (err, shouldSucceed) match
  {
    case (None, true) | (Some(_), false) => ()
    case (None, false) => sys.error("Expected failure")
    case (Some(x), true) => throw x
  }
}
