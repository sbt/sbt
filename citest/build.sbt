lazy val check = taskKey[Unit]("")
lazy val check2 = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.4",
    name := "Hello",
    check := {
      val xs = IO.readLines(file("output.txt")).toVector

      println(xs)
      assert(xs(0) startsWith "[info] Loading project definition")
      assert(xs(1) startsWith "[info] Loading settings")
      assert(xs(2) startsWith "[info] Set current project to Hello")
      assert(xs(3) startsWith "[info] This is sbt")
      assert(xs(4) startsWith "[info] The current project")

      val ys = IO.readLines(file("err.txt")).toVector.distinct

      assert(ys.isEmpty, s"there's an stderr: $ys")
    }
  )
