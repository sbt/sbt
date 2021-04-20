import sbt.internal.inc.classpath.ClasspathUtilities

lazy val scalaOverride = taskKey[Unit]("Check that the proper version of Scala is on the classpath.")

lazy val root = (project in file(".")).
  settings(
    libraryDependencies ++= baseDirectory(dependencies).value,
    scalaVersion := "2.9.2",
    scalaModuleInfo := scalaModuleInfo.value map (_.withOverrideScalaVersion(sbtPlugin.value)),
    autoScalaLibrary := baseDirectory(base => !(base / "noscala").exists ).value,
    scalaOverride := check("scala.App").value
  )

def check(className: String): Def.Initialize[Task[Unit]] = fullClasspath in Compile map { cp =>
  val existing = cp.files.filter(_.getName contains "scala-library")
  println("Full classpath: " + cp.mkString("\n\t", "\n\t", ""))
  println("scala-library.jar: " + existing.mkString("\n\t", "\n\t", ""))
  val loader = ClasspathUtilities.toLoader(existing)
  Class.forName(className, false, loader)
}

def dependencies(base: File) =
  if( ( base / "stm").exists ) ("org.scala-tools" % "scala-stm_2.8.2" % "0.6") :: Nil
  else Nil
