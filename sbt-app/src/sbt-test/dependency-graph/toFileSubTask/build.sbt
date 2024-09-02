// ThisBuild / useCoursier := false
ThisBuild / scalaVersion := "2.12.20"
ThisBuild / organization := "org.example"
ThisBuild / version := "0.1"

name := "blubber"
libraryDependencies += "org.typelevel" %% "cats-effect" % "2.2.0"
TaskKey[Unit]("check") := {
  val candidates = "tree list stats licenses".split(' ').map(_.trim)
  candidates.foreach { c =>
    val expected = new File(s"expected/$c.txt")
    val actual = new File(s"target/$c.txt")

    import sys.process._
    val exit = s"diff -U3 ${expected.getPath} ${actual.getPath}".!
    require(exit == 0, s"Diff was non-zero for ${actual.getName}")
  }

  //require(sanitize(graph) == sanitize(expectedGraph), "Graph for report %s was '\n%s' but should have been '\n%s'" format (report, sanitize(graph), sanitize(expectedGraph)))
  ()
}
