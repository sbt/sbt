val rootRef = LocalProject("root")
val sub = project
val superRoot = project in file("super") dependsOn rootRef

val root = (project in file(".")).
  dependsOn(sub % "provided->test").
  settings (
    TaskKey[Unit]("check") := {
      check0((sub / Test / fullClasspath).value, "sub test", true)
      check0((superRoot / Compile / fullClasspath).value, "superRoot main", false)
      check0((rootRef / Compile / fullClasspath).value, "root main", true)
      check0((rootRef / Runtime / fullClasspath).value, "root runtime", false)
      check0((rootRef / Test / fullClasspath).value, "root test", true)
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
