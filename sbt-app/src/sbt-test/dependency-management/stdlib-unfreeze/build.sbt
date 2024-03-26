import sbt.librarymanagement.InclExclRule

lazy val a = project.settings(
  scalaVersion := "2.13.6",
  libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  TaskKey[Unit]("checkLibs") := checkLibs("2.13.6", (Compile/dependencyClasspath).value, ".*scala-(library|reflect).*"),
)

lazy val b = project.dependsOn(a).settings(
  scalaVersion := "2.13.8",
  TaskKey[Unit]("checkLibs") := checkLibs("2.13.8", (Compile/dependencyClasspath).value, ".*scala-(library|reflect).*"),
)

lazy val a3 = project.settings(
  scalaVersion := "3.2.2", // 2.13.10 library
)

lazy val b3 = project.dependsOn(a3).settings(
  scalaVersion := "3.2.0", // 2.13.8 library
  TaskKey[Unit]("checkScala") := {
    val i = scalaInstance.value
    i.libraryJars.filter(_.toString.contains("scala-library")).toList match {
      case List(l) => assert(l.toString.contains("2.13.10"), i.toString)
    }
    assert(i.compilerJars.filter(_.toString.contains("scala-library")).isEmpty, i.toString)
    assert(i.otherJars.filter(_.toString.contains("scala-library")).isEmpty, i.toString)
  },
)

lazy val ak = project.settings(
  scalaVersion := "2.13.12",
  csrSameVersions += Set[InclExclRule]("com.typesafe.akka" % "akka-*"),
  libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-remote" % "2.6.5",
    "com.typesafe.akka" %% "akka-actor" % "2.6.2",
  ),
  TaskKey[Unit]("checkLibs") := checkLibs("2.6.5", (Compile/dependencyClasspath).value, ".*akka-.*"),
)

def checkLibs(v: String, cp: Classpath, filter: String): Unit = {
  for (p <- cp)
    if (p.toString.matches(filter)) {
      println(s"$p -- $v")
      assert(p.toString.contains(v), p)
    }
}
