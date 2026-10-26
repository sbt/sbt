/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package example.test

import java.io.File
import sbt.io.IO
import scala.sys.process.{ Process, ProcessLogger }

/**
 * First-hop propagation guard: an explicit `-java-home` must be the JDK the Windows launcher runs
 * (echoed on the verbose command line). Not the full sbt/sbt#963 regression — the
 * `.java-version`-override case needs two JDK paths + the native client, so it is verified manually
 * on Windows.
 */
object JavaHomeScriptTest extends verify.BasicTestSuite with ShellScriptUtil:
  private val jdkHome =
    new File(sys.env.getOrElse("JAVA_HOME", System.getProperty("java.home"))).getAbsolutePath

  testOutput("sbt -java-home selects the JDK for the launcher")(
    "-java-home",
    jdkHome,
    "compile",
    "-v"
  ): (out: List[String]) =>
    if !isWindows then cancel("`-java-home` bin/java.exe selection is Windows-specific")
    else
      val javaExe = new File(new File(jdkHome, "bin"), "java.exe").getAbsolutePath
      assert(
        out.exists(_.contains(javaExe)),
        s"launcher should run $javaExe; command echo was: ${out.mkString(" | ")}"
      )
  List(
    ("JAVA_HOME overrides PATH", "", "home", "", "home"),
    ("JAVACMD overrides JAVA_HOME", "command", "home", "", "command"),
    ("invalid JAVACMD falls back to JAVA_HOME", "missing", "home", "", "home"),
    ("empty JAVA_HOME falls back to PATH", "", "", "", "path"),
    ("invalid JAVA_HOME falls back to PATH", "", "missing", "", "path"),
    ("invalid JAVACMD and JAVA_HOME fall back to PATH", "missing", "missing", "", "path"),
    ("--java-home overrides environment", "command", "home", "option", "option"),
  ).foreach: (name, command, home, option, expected) =>
    test(name):
      checkJavaSelection(command, home, option, expected)

  private def checkJavaSelection(
      command: String,
      home: String,
      option: String,
      expected: String
  ): Unit =
    if isWindows then cancel("Bash launcher Java selection")
    else
      IO.withTemporaryDirectory: dir =>
        def javaHome(name: String): File = new File(dir, s"$name jdk")
        def java(name: String): File = new File(javaHome(name), "bin/java")
        List("path", "home", "command", "option").foreach: name =>
          IO.write(
            java(name),
            IO.read(new File(javaBinDir, "java"))
              .replace("else:\n", s"else:\n    print('SELECTED: $name')\n")
          )
          Predef.assert(java(name).setExecutable(true))
        IO.write(new File(dir, "build.sbt"), "")
        val env = Seq(
          "PATH" -> s"${java("path").getParent}${File.pathSeparator}${sys.env("PATH")}",
          "JAVACMD" -> (if command.isEmpty then "" else java(command).getAbsolutePath),
          "JAVA_HOME" -> (if home.isEmpty then "" else javaHome(home).getAbsolutePath),
          "JAVA_OPTS" -> "",
          "SBT_OPTS" -> "",
          "JAVA_TOOL_OPTIONS" -> "",
          "XDG_CONFIG_HOME" -> dir.getAbsolutePath,
          "SBT_ETC_FILE" -> new File(dir, "missing-sbtopts").getAbsolutePath,
        )
        val args = if option.isEmpty then Seq.empty
        else Seq("--java-home", javaHome(option).getAbsolutePath)
        val output = scala.collection.mutable.ListBuffer.empty[String]
        val exit = Process(
          Seq(sbtScript.getAbsolutePath, "--server") ++ args ++ Seq("compile"),
          dir,
          env*
        ).!(ProcessLogger(line => output += line, line => output += line))
        Predef.assert(exit == 0, output.mkString("\n"))
        Predef.assert(output.contains(s"SELECTED: $expected"), output.mkString("\n"))
end JavaHomeScriptTest
