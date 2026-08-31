// Regression test for #9698: a project on Scala 3.7 (below 3.8) pulls in an ordinary external
// dependency (not an inter-project dependsOn) that needs scala-library 3.8+, which also needs
// scala-reflect. Without the fix, csrSameVersions unifies scala-reflect with scala-library and
// tries to resolve scala-reflect on the 3.x line, where it has never been published.
lazy val root = project.settings(
  scalaVersion := "3.7.4",
  libraryDependencies += "com.lihaoyi" %% "mill-libs-scalalib" % "1.1.8",
  TaskKey[Unit]("checkLibs") := checkLibs((Compile / dependencyClasspath).value),
)

def checkLibs(cp: Seq[Attributed[File]]): Unit = {
  val reflect = cp.map(_.data.toString).find(_.contains("scala-reflect"))
  assert(reflect.exists(_.contains("2.13")), s"expected scala-reflect on the 2.13 line, got: $reflect")

  val library = cp.map(_.data.toString).filter(p => p.contains("scala-library") && !p.contains("scala3-library"))
  assert(library.exists(_.contains("3.8")), s"expected scala-library on the 3.8+ line, got: $library")
}
