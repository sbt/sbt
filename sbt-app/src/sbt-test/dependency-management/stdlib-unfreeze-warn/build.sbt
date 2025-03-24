import sbt.librarymanagement.InclExclRule

lazy val a = project.settings(
  scalaVersion := "2.13.13",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  TaskKey[Unit]("checkLibs") := checkLibs("2.13.13", (Compile/dependencyClasspath).value, ".*scala-(library|reflect).*"),
)

lazy val b = project.dependsOn(a).settings(
  allowUnsafeScalaLibUpgrade := true,
  scalaVersion := "2.13.12",

  // dependencies are upgraded to 2.13.13
  TaskKey[Unit]("checkLibs") := checkLibs("2.13.13", (Compile/dependencyClasspath).value, ".*scala-(library|reflect).*"),

  // check the compiler uses the 2.13.12 library on its runtime classpath
  TaskKey[Unit]("checkScala") := {
    val i = scalaInstance.value
    i.libraryJars.filter(_.toString.contains("scala-library")).toList match {
      case List(l) => assert(l.toString.contains("2.13.12"), i.toString)
    }
    assert(i.compilerJars.filter(_.toString.contains("scala-library")).isEmpty, i.toString)
    assert(i.otherJars.filter(_.toString.contains("scala-library")).isEmpty, i.toString)
  },
)

lazy val c = project.dependsOn(a).settings(
  allowUnsafeScalaLibUpgrade := true,
  scalaVersion := "2.13.12",
  TaskKey[Unit]("checkLibs") := checkLibs("2.13.13", (Compile/dependencyClasspath).value, ".*scala-(library|reflect).*"),
)

def checkLibs(v: String, cp: Classpath, filter: String): Unit = {
  for (p <- cp)
    if (p.toString.matches(filter)) {
      println(s"$p -- $v")
      assert(p.toString.contains(v), p)
    }
}
