lazy val check = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.4",
    name := "Hello",
    check := {
      val xs = IO.readLines(file("output.txt")).toVector

      println(xs)

      assert(xs(0) startsWith "Copying runtime jar.")
      // echo of jar name
      assert(xs(2) startsWith "[info] Loading project definition")
      assert(xs(3) startsWith "[info] Loading settings from build.sbt")
      assert(xs(4) startsWith "[info] Set current project to Hello")
      assert(xs(5) startsWith "[info] This is sbt")
      assert(xs(6) startsWith "[info] The current project")
      assert(xs(7) startsWith "[info] The current project is built against Scala 2.12.4")

      val ys = IO.readLines(file("err.txt")).toVector

      println(ys)

      assert(ys.size == 4)
      assert(ys(0) startsWith "Error: Unable to access jarfile")
      assert(ys(1) startsWith "The filename, directory name, or volume label syntax is incorrect.")
      assert(ys(2) startsWith "Error: Unable to access jarfile")
      assert(ys(3) startsWith "Java HotSpot(TM) 64-Bit Server VM warning")
    }
  )
