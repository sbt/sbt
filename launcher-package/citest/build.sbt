lazy val check = taskKey[Unit]("")
lazy val check2 = taskKey[Unit]("")
lazy val checkEvalArgHandling = taskKey[Unit]("")
lazy val checkDArgHandling = taskKey[Unit]("")
lazy val checkXXArgHandling = taskKey[Unit]("")

lazy val root = (project in file("."))
  .settings(
    scalaVersion := "3.8.4",
    name := "Hello",
    libraryDependencies += "com.eed3si9n.verify" %% "verify" % "1.0.0" % Test,
    testFrameworks += new TestFramework("verify.runner.Framework"),
    check := {
      val xs = IO.readLines(file("output.txt")).toVector

      println(xs)
      assert(xs(0) contains "welcome to sbt")
      assert(xs(1) contains "loading project definition")
      assert(xs(2) contains "loading settings")

      val ys = IO.readLines(file("err.txt")).toVector.distinct

      assert(ys.isEmpty, s"there's an stderr: $ys")
    },
    // Regression check for https://github.com/sbt/sbt/issues/9660, run as a
    // real .bat command by test.bat rather than via a JVM-constructed command
    // line, so cmd.exe parses the argument the same way it would if a person
    // had typed it at a prompt.
    checkEvalArgHandling := {
      val evalOut = IO.readLines(file("evalOutput.txt")).toVector
      println(evalOut)
      assert(
        evalOut.exists(_.contains("barqux")),
        s"""eval ("bar") ++ ("qux") should print barqux, but got: $evalOut"""
      )
      assert(
        !evalOut.exists(l =>
          l.contains("was unexpected at this time") || l.contains("is not recognized")
        ),
        s"eval with quoted parens should not trigger a cmd.exe parse error: $evalOut"
      )

      assert(
        !file("injected.txt").exists,
        "the & in the eval argument must not escape sbt.bat's quoting and run as a separate command"
      )
    },
    // Regression check for https://github.com/sbt/sbt/issues/9660, matching a
    // reporter-confirmed case: "sbt" "-Dfoo=()&calc" pops calc when typed at a
    // real cmd.exe prompt, even though the whole value sits inside one clean,
    // matched pair of quotes (no embedded quote characters to lose parity).
    checkDArgHandling := {
      assert(
        !file("injected2.txt").exists,
        "the & in a -D argument value must not escape sbt.bat's quoting and run as a separate command"
      )
    },
    // Regression check for https://github.com/sbt/sbt/issues/9660: the -XX
    // handling in args_loop has the same shape as -D, so the same reporter-
    // confirmed bypass applies to it.
    checkXXArgHandling := {
      assert(
        !file("injected3.txt").exists,
        "the & in a -XX argument value must not escape sbt.bat's quoting and run as a separate command"
      )
    }
  )
