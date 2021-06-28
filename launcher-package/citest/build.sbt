lazy val check = taskKey[Unit]("")
lazy val check2 = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.4",
    name := "Hello",
    libraryDependencies += "com.eed3si9n.verify" %% "verify" % "0.2.0" % Test,
    testFrameworks += new TestFramework("verify.runner.Framework"),
    check := {
      val xs = IO.readLines(file("output.txt")).toVector

      println(xs)
      assert(xs(0) contains "welcome to sbt")
      assert(xs(1) contains "loading project definition")
      assert(xs(2) contains "loading settings")

      val ys = IO.readLines(file("err.txt")).toVector.distinct

      assert(ys.isEmpty, s"there's an stderr: $ys")
    }
  )
