val rootRef = LocalProject("root")
val sub = project
val superRoot = (project in file("super")).dependsOn(rootRef)

lazy val root = (project in file("."))
  .dependsOn(sub % "provided->test")
  .settings(
    TaskKey[Unit]("check") := {
      val conv = fileConverter.value
      check0((sub / Test / fullClasspath).value, "sub test", true, conv)
      check0((superRoot / Compile / fullClasspath).value, "superRoot main", false, conv)
      check0((rootRef / Compile / fullClasspath).value, "root main", true, conv)
      check0((rootRef / Runtime / fullClasspath).value, "root runtime", false, conv)
      check0((rootRef / Test / fullClasspath).value, "root test", true, conv)
    }
  )

def check0(cp: Seq[Attributed[HashedVirtualFileRef]],
    label: String, shouldSucceed: Boolean, conv: FileConverter): Unit =
  import sbt.internal.inc.classpath.ClasspathUtilities
  val cp1 = cp.map: a =>
    conv.toPath(a.data).toFile()
  val loader = ClasspathUtilities.toLoader(cp1)
  println("Checking " + label)
  val err =
    try { Class.forName("org.example.ProvidedTest", false, loader); None }
    catch { case e: Exception => Some(e) }
  (err, shouldSucceed) match
    case (None, true) | (Some(_), false) => ()
    case (None, false)                   => sys.error("Expected failure")
    case (Some(x), true)                 => throw x
