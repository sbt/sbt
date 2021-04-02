val rootRef = LocalProject("root")
val sub = project
val superRoot = project in file("super") dependsOn rootRef

val root = project in file(".") dependsOn (sub % "provided->test") settings (
  TaskKey[Unit]("check") := {
    check0((fullClasspath in (sub, Test)).value, "sub test", true)
    check0((fullClasspath in (superRoot, Compile)).value, "superRoot main", false)
    check0((fullClasspath in (rootRef, Compile)).value, "root main", true)
    check0((fullClasspath in (rootRef, Runtime)).value, "root runtime", false)
    check0((fullClasspath in (rootRef, Test)).value, "root test", true)
  }
)

def check0(cp: Seq[Attributed[File]], label: String, shouldSucceed: Boolean): Unit = {
  import sbt.internal.inc.classpath.ClasspathUtilities
  val loader = ClasspathUtilities.toLoader(cp.files)
  println("Checking " + label)
  val err = try { Class.forName("org.example.ProvidedTest", false, loader); None }
  catch { case e: Exception => Some(e) }

  (err, shouldSucceed) match {
    case (None, true) | (Some(_), false) => ()
    case (None, false)                   => sys.error("Expected failure")
    case (Some(x), true)                 => throw x
  }
}
