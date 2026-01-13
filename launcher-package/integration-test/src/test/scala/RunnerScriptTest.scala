package example.test

import java.io.File
import java.nio.file.Files
import sbt.io.IO

/**
 * RunnerScriptTest is used to test the sbt shell script, for both macOS/Linux and Windows.
 */
object RunnerScriptTest extends verify.BasicTestSuite with ShellScriptUtil:

  testOutput("sbt -no-colors")("compile", "-no-colors", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.log.noformat=true"))

  testOutput("sbt --no-colors")("compile", "--no-colors", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.log.noformat=true"))

  testOutput("sbt --color=false")("compile", "--color=false", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.color=false"))

  testOutput("sbt --no-colors in SBT_OPTS", sbtOpts = "--no-colors")("compile", "-v"):
    (out: List[String]) =>
      if (isWindows) cancel("Test not supported on windows")
      assert(out.contains[String]("-Dsbt.log.noformat=true"))

  testOutput("sbt --no-server")("compile", "--no-server", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.server.autostart=false"))

  testOutput("sbt --debug-inc")("compile", "--debug-inc", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dxsbt.inc.debug=true"))

  testOutput("sbt --supershell=never")("compile", "--supershell=never", "-v"):
    (out: List[String]) => assert(out.contains[String]("-Dsbt.supershell=never"))

  testOutput("sbt --timings")("compile", "--timings", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.task.timings=true"))

  testOutput("sbt -D arguments")("-Dsbt.supershell=false", "compile", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.supershell=false"))

  testOutput("sbt --sbt-version")("--sbt-version", "1.3.13", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.version=1.3.13"))

  testOutput(
    name = "sbt with -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 in SBT_OPTS",
    sbtOpts = "-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080",
  )("-v"): (out: List[String]) =>
    assert(out.contains[String]("-Dhttp.proxyHost=proxy"))
    assert(out.contains[String]("-Dhttp.proxyPort=8080"))

  testOutput(
    name = "sbt with -XX:ParallelGCThreads=16 -XX:PermSize=128M in SBT_OPTS",
    sbtOpts = "-XX:ParallelGCThreads=16 -XX:PermSize=128M",
  )("-v"): (out: List[String]) =>
    assert(out.contains[String]("-XX:ParallelGCThreads=16"))
    assert(out.contains[String]("-XX:PermSize=128M"))

  testOutput(
    "sbt with -XX:+UseG1GC -XX:+PrintGC in JAVA_OPTS",
    javaOpts = "-XX:+UseG1GC -XX:+PrintGC"
  )("-v"): (out: List[String]) =>
    assert(out.contains[String]("-XX:+UseG1GC"))
    assert(out.contains[String]("-XX:+PrintGC"))
    assert(!out.contains[String]("-XX:+UseG1GC=-XX:+PrintGC"))

  testOutput(
    "sbt with -XX:-UseG1GC -XX:-PrintGC in SBT_OPTS",
    sbtOpts = "-XX:+UseG1GC -XX:+PrintGC"
  )(
    "-v"
  ): (out: List[String]) =>
    assert(out.contains[String]("-XX:+UseG1GC"))
    assert(out.contains[String]("-XX:+PrintGC"))
    assert(!out.contains[String]("-XX:+UseG1GC=-XX:+PrintGC"))

  testOutput(
    "sbt with -debug in SBT_OPTS appears in sbt commands",
    javaOpts = "",
    sbtOpts = "-debug"
  )("compile", "-v"): (out: List[String]) =>
    if (isWindows) cancel("Test not supported on windows")

    // Debug argument must appear in the 'commands' section (after the sbt-launch.jar argument) to work
    val sbtLaunchMatcher = """^.+sbt-launch.jar["]{0,1}$""".r
    val locationOfSbtLaunchJarArg = out.zipWithIndex.collectFirst {
      case (arg, index) if sbtLaunchMatcher.findFirstIn(arg).nonEmpty => index
    }

    assert(locationOfSbtLaunchJarArg.nonEmpty)

    val argsAfterSbtLaunch = out.drop(locationOfSbtLaunchJarArg.get)
    assert(argsAfterSbtLaunch.contains("-debug"))
    ()

  testOutput("sbt --jvm-debug <port>")("--jvm-debug", "12345", "-v"): (out: List[String]) =>
    assert(
      out.contains[String]("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=12345")
    )

  // Regression test for https://github.com/sbt/sbt/issues/8100
  // Debug agent output in SBT_OPTS should not break the launcher on Windows
  testOutput(
    "sbt with debug agent in SBT_OPTS",
    sbtOpts = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=12346"
  )("-v"): (out: List[String]) =>
    assert(
      out.contains[String]("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=12346")
    )

  testOutput("sbt --no-share adds three system properties")("--no-share"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.global.base=project/.sbtboot"))
    assert(out.contains[String]("-Dsbt.boot.directory=project/.boot"))
    assert(out.contains[String]("-Dsbt.ivy.home=project/.ivy"))

  testOutput("accept `--ivy` in `SBT_OPTS`", sbtOpts = "--ivy /ivy/dir")("-v"):
    (out: List[String]) =>
      if (isWindows) cancel("Test not supported on windows")
      else assert(out.contains[String]("-Dsbt.ivy.home=/ivy/dir"))

  testOutput("sbt --script-version should print sbtVersion")("--script-version"):
    (out: List[String]) =>
      val expectedVersion = "^" + ExtendedRunnerTest.versionRegEx + "$"
      assert(out.mkString(System.lineSeparator()).trim.matches(expectedVersion))
      ()

  testOutput("--sbt-cache")("--sbt-cache", "./cachePath"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.global.localcache=./cachePath"))

  // Test for issue #7179: sbtopts files priority
  test("project .sbtopts overrides dist sbtopts") {
    if (isWindows) cancel("Test not supported on windows")
    else
      val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
      retry(() => IO.copyDirectory(new File("launcher-package/citest"), workingDirectory))

      try
        // Create dist sbtopts
        val sbtHome = Files.createTempDirectory("sbt-home").toFile
        val distSbtoptsDir = new File(sbtHome, "conf")
        distSbtoptsDir.mkdirs()
        val distSbtoptsFile = new File(distSbtoptsDir, "sbtopts")
        IO.write(distSbtoptsFile, "-Dsbt.test.config=dist-default")

        // Create project .sbtopts
        val projectSbtoptsFile = new File(workingDirectory, ".sbtopts")
        IO.write(projectSbtoptsFile, "-Dsbt.test.config=project-local")

        val path = sys.env.getOrElse("PATH", sys.env("Path"))
        val out = scala.sys.process
          .Process(
            Seq(sbtScript.getAbsolutePath, "-d", "-v"),
            workingDirectory,
            "SBT_HOME" -> sbtHome.getAbsolutePath,
            if (isWindows)
              "JAVACMD" -> new File(javaBinDir, "java").getAbsolutePath()
            else
              "PATH" -> (javaBinDir + File.pathSeparator + path)
          )
          .!!
          .linesIterator
          .toList

        // Find the command line section
        val cmdLineStart = out.indexWhere(_.contains("Executing command line"))
        assert(cmdLineStart >= 0, "Command line section not found")

        val cmdLine = out.drop(cmdLineStart + 1).takeWhile(!_.trim.isEmpty)
        val distIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=dist-default"))
        val projectIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=project-local"))

        assert(distIndex >= 0, "Dist config not found in command line")
        assert(projectIndex >= 0, "Project config not found in command line")
        assert(
          projectIndex > distIndex,
          s"Project config should appear after dist config. distIndex=$distIndex, projectIndex=$projectIndex"
        )
        ()
      finally
        IO.delete(workingDirectory)
        IO.delete(sbtHome)
  }

  test("project .sbtopts overrides machine sbtopts") {
    if (isWindows) cancel("Test not supported on windows")
    else
      val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
      retry(() => IO.copyDirectory(new File("launcher-package/citest"), workingDirectory))

      try
        // Create machine sbtopts
        val configHome = Files.createTempDirectory("config-home").toFile
        val machineSbtoptsDir = new File(configHome, "sbt")
        machineSbtoptsDir.mkdirs()
        val machineSbtoptsFile = new File(machineSbtoptsDir, "sbtopts")
        IO.write(machineSbtoptsFile, "-Dsbt.test.config=machine-config")

        // Create project .sbtopts
        val projectSbtoptsFile = new File(workingDirectory, ".sbtopts")
        IO.write(projectSbtoptsFile, "-Dsbt.test.config=project-local")

        val path = sys.env.getOrElse("PATH", sys.env("Path"))
        val out = scala.sys.process
          .Process(
            Seq(sbtScript.getAbsolutePath, "-d", "-v"),
            workingDirectory,
            "XDG_CONFIG_HOME" -> configHome.getAbsolutePath,
            if (isWindows)
              "JAVACMD" -> new File(javaBinDir, "java").getAbsolutePath()
            else
              "PATH" -> (javaBinDir + File.pathSeparator + path)
          )
          .!!
          .linesIterator
          .toList

        // Find the command line section
        val cmdLineStart = out.indexWhere(_.contains("Executing command line"))
        assert(cmdLineStart >= 0, "Command line section not found")

        val cmdLine = out.drop(cmdLineStart + 1).takeWhile(!_.trim.isEmpty)
        val machineIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=machine-config"))
        val projectIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=project-local"))

        assert(machineIndex >= 0, "Machine config not found in command line")
        assert(projectIndex >= 0, "Project config not found in command line")
        assert(
          projectIndex > machineIndex,
          s"Project config should appear after machine config. machineIndex=$machineIndex, projectIndex=$projectIndex"
        )
        ()
      finally
        IO.delete(workingDirectory)
        IO.delete(configHome)
  }

  test("project .sbtopts overrides both dist and machine sbtopts") {
    if (isWindows) cancel("Test not supported on windows")
    else
      val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
      retry(() => IO.copyDirectory(new File("launcher-package/citest"), workingDirectory))

      try
        // Create dist sbtopts
        val sbtHome = Files.createTempDirectory("sbt-home").toFile
        val distSbtoptsDir = new File(sbtHome, "conf")
        distSbtoptsDir.mkdirs()
        val distSbtoptsFile = new File(distSbtoptsDir, "sbtopts")
        IO.write(distSbtoptsFile, "-Dsbt.test.config=dist-default")

        // Create machine sbtopts
        val configHome = Files.createTempDirectory("config-home").toFile
        val machineSbtoptsDir = new File(configHome, "sbt")
        machineSbtoptsDir.mkdirs()
        val machineSbtoptsFile = new File(machineSbtoptsDir, "sbtopts")
        IO.write(machineSbtoptsFile, "-Dsbt.test.config=machine-config")

        // Create project .sbtopts
        val projectSbtoptsFile = new File(workingDirectory, ".sbtopts")
        IO.write(projectSbtoptsFile, "-Dsbt.test.config=project-local")

        val path = sys.env.getOrElse("PATH", sys.env("Path"))
        val out = scala.sys.process
          .Process(
            Seq(sbtScript.getAbsolutePath, "-d", "-v"),
            workingDirectory,
            "SBT_HOME" -> sbtHome.getAbsolutePath,
            "XDG_CONFIG_HOME" -> configHome.getAbsolutePath,
            if (isWindows)
              "JAVACMD" -> new File(javaBinDir, "java").getAbsolutePath()
            else
              "PATH" -> (javaBinDir + File.pathSeparator + path)
          )
          .!!
          .linesIterator
          .toList

        // Find the command line section
        val cmdLineStart = out.indexWhere(_.contains("Executing command line"))
        assert(cmdLineStart >= 0, "Command line section not found")

        val cmdLine = out.drop(cmdLineStart + 1).takeWhile(!_.trim.isEmpty)
        val distIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=dist-default"))
        val machineIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=machine-config"))
        val projectIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=project-local"))

        assert(distIndex >= 0, "Dist config not found in command line")
        assert(machineIndex >= 0, "Machine config not found in command line")
        assert(projectIndex >= 0, "Project config not found in command line")
        assert(
          distIndex < machineIndex,
          s"Dist config should appear before machine config. distIndex=$distIndex, machineIndex=$machineIndex"
        )
        assert(
          machineIndex < projectIndex,
          s"Machine config should appear before project config. machineIndex=$machineIndex, projectIndex=$projectIndex"
        )
        ()
      finally
        IO.delete(workingDirectory)
        IO.delete(sbtHome)
        IO.delete(configHome)
  }

end RunnerScriptTest
