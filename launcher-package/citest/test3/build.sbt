lazy val check = taskKey[Unit]("")
lazy val checkNumericVersion = taskKey[Unit]("")
lazy val checkScriptVersion = taskKey[Unit]("")
lazy val checkVersion = taskKey[Unit]("")

// 1.3.0, 1.3.0-M4
lazy val versionRegEx = "\\d(\\.\\d+){2}(-\\w+)?"

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "2.12.4",
    name := "Hello",
    check := {
      val xs = IO.readLines(file("output.txt")).toVector

      println(xs)

      assert(xs(0) startsWith "[info] Loading project definition")
      assert(xs(1) startsWith "[info] Loading settings from build.sbt")
      assert(xs(2) startsWith "[info] Set current project to Hello")
      assert(xs(3) startsWith "[info] This is sbt")
      assert(xs(4) startsWith "[info] The current project")
      assert(xs(5) startsWith "[info] The current project is built against Scala 2.12.4")

      val ys = IO.readLines(file("err.txt")).toVector.distinct

      assert(ys.size == 1, s"ys has more than one item: $ys")
      assert(ys(0) startsWith "Java HotSpot(TM) 64-Bit Server VM warning")
    },
    checkNumericVersion = {
      val xs = IO.readLines(file("numericVersion.txt")).toVector
      val expectedVersion = "^"+versionRegEx+"$"

      assert(xs(0).matches(expectedVersion))
    },
    checkScriptVersion = {
      val xs = IO.readLines(file("scriptVersion.txt")).toVector
      val expectedVersion = "^"+versionRegEx+"$"

      assert(xs(0).matches(expectedVersion))
    },
    checkVersion = {
      val out = IO.readLines(file("version.txt")).toVector.mkString("\n")

      val expectedVersion =
        s"""|(?m)^sbt version in this project: $versionRegEx
           |sbt script version: $versionRegEx$$
           |""".stripMargin.trim.replace("\n", "\\n")

      assert(out.matches(expectedVersion))
    }
  )
