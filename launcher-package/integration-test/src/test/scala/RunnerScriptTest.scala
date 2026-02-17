package example.test

/**
 * RunnerScriptTest is used to test the sbt shell script, for both macOS/Linux and Windows.
 */
object RunnerScriptTest extends verify.BasicTestSuite with ShellScriptUtil:
  private val versionPattern = "\\d(\\.\\d+){2}(-\\w+)?"

  private def assertScriptVersion(out: List[String]): Unit =
    assert(out.mkString(System.lineSeparator()).trim.matches("^" + versionPattern + "$"))

  private def assertVersionOutput(out: List[String]): Unit =
    val lines =
      out.mkString(System.lineSeparator()).linesIterator.map(_.stripPrefix("[0J").trim).toList
    assert(
      lines.exists(_.matches("^sbt version in this project: " + versionPattern + "\\r?$")) ||
        lines.contains("sbtVersion")
    )
    assert(lines.exists(_.matches("^sbt runner version: " + versionPattern + "\\r?$")))
    assert(!lines.exists(_.contains("failed to connect to server")))

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

  testOutput(
    "sbt --script-version should print sbtVersion (sbt 1.x project)",
    citestVariant = "citest",
  )("--script-version"): (out: List[String]) =>
    assertScriptVersion(out)
    ()

  testOutput(
    "sbt --script-version should print sbtVersion (sbt 2.x project)",
    citestVariant = "citest2",
  )("--script-version"): (out: List[String]) =>
    assertScriptVersion(out)
    ()

  testOutput(
    "sbt --version should work (sbt 1.x project)",
    citestVariant = "citest",
  )("--version"): (out: List[String]) =>
    assertVersionOutput(out)
    ()

  testOutput(
    "sbt --version should work (sbt 2.x project)",
    citestVariant = "citest2",
  )("--version"): (out: List[String]) =>
    assertVersionOutput(out)
    ()

  testOutput("--sbt-cache")("--sbt-cache", "./cachePath"): (out: List[String]) =>
    assert(out.contains[String]("-Dsbt.global.localcache=./cachePath"))

  // Test for issue #7179: sbtopts files priority
  testOutput(
    "project .sbtopts overrides dist sbtopts",
    distSbtoptsContents = "-Dsbt.test.config=dist-default",
    sbtOptsFileContents = "-Dsbt.test.config=project-local"
  )("-d", "-v"): (out: List[String]) =>
    if (isWindows) cancel("Test not supported on windows")
    else
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

  testOutput(
    "project .sbtopts overrides machine sbtopts",
    machineSbtoptsContents = "-Dsbt.test.config=machine-config",
    sbtOptsFileContents = "-Dsbt.test.config=project-local"
  )("-d", "-v"): (out: List[String]) =>
    if (isWindows) cancel("Test not supported on windows")
    else
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

  testOutput(
    "project .sbtopts overrides both dist and machine sbtopts",
    distSbtoptsContents = "-Dsbt.test.config=dist-default",
    machineSbtoptsContents = "-Dsbt.test.config=machine-config",
    sbtOptsFileContents = "-Dsbt.test.config=project-local"
  )("-d", "-v"): (out: List[String]) =>
    if (isWindows) cancel("Test not supported on windows")
    else
      // Find the command line section
      val cmdLineStart = out.indexWhere(_.contains("Executing command line"))
      assert(cmdLineStart >= 0, "Command line section not found")

      val cmdLine = out.drop(cmdLineStart + 1).takeWhile(!_.trim.isEmpty)
      val distIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=dist-default"))
      val machineIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=machine-config"))
      val projectIndex = cmdLine.indexWhere(_.contains("Dsbt.test.config=project-local"))

      // When machine sbtopts exists, the script only loads machine (not dist) due to if-else structure
      // So dist should NOT be present, but machine and project should be
      assert(distIndex < 0, "Dist config should NOT be present when machine config exists")
      assert(machineIndex >= 0, "Machine config not found in command line")
      assert(projectIndex >= 0, "Project config not found in command line")
      assert(
        machineIndex < projectIndex,
        s"Machine config should appear before project config. machineIndex=$machineIndex, projectIndex=$projectIndex"
      )

  testOutput(
    "command line options override project .sbtopts",
    sbtOptsFileContents =
      "-J-Xmx2g\n-J-XX:ReservedCodeCacheSize=1g\n-J-XX:MaxMetaspaceSize=2g\n-J-Xss512m\n-J-XX:+UseG1GC"
  )("-d", "-v", "-mem", "12288"): (out: List[String]) =>
    if (isWindows) cancel("Test not supported on windows")
    else
      val cmdLineStart = out.indexWhere(_.contains("Executing command line"))
      assert(cmdLineStart >= 0, "Command line section not found")

      val cmdLine = out.drop(cmdLineStart + 1).takeWhile(!_.trim.isEmpty)
      val xmxCliIndex = cmdLine.indexWhere(_.contains("-Xmx12288m"))
      val xmxSbtoptsIndex = cmdLine.indexWhere(_.contains("-Xmx2g"))
      val g1Index = cmdLine.indexWhere(_.contains("-XX:+UseG1GC"))

      assert(xmxCliIndex >= 0, "CLI memory setting not found in command line")
      assert(xmxSbtoptsIndex < 0, "sbtopts -Xmx2g should be overridden by CLI")
      assert(g1Index >= 0, "sbtopts non-memory option not found in command line")
      assert(
        g1Index < xmxCliIndex,
        s"sbtopts options should appear before CLI memory settings. g1Index=$g1Index, xmxCliIndex=$xmxCliIndex"
      )

  // Test for issue #7333: JVM parameters with spaces in .sbtopts
  testOutput(
    "sbt with -J--add-modules jdk.incubator.concurrent in .sbtopts (args with spaces)",
    sbtOptsFileContents = "-J--add-modules jdk.incubator.concurrent",
    windowsSupport = false,
  )("-v"): (out: List[String]) =>
    assert(out.contains[String]("--add-modules"))
    assert(out.contains[String]("jdk.incubator.concurrent"))

  // Test for issue #7333: -D with spaces in .jvmopts
  testOutput(
    "sbt with -Dkey=\"value with spaces\" in .jvmopts",
    jvmoptsFileContents = """-Dtest.7333="value with spaces"""",
    windowsSupport = false,
  )("-v"): (out: List[String]) =>
    assert(
      out.exists(_.contains("test.7333")),
      s"Expected -Dtest.7333= in output, got: ${out.filter(_.contains("test.7333")).mkString(", ")}"
    )
    assert(
      out.exists(_.contains("value with spaces")),
      "Expected 'value with spaces' in -D value"
    )

  // Test for issue #7289: Special characters in .jvmopts should not cause shell expansion
  testOutput(
    "sbt with special characters in .jvmopts (pipes, wildcards, ampersands)",
    jvmoptsFileContents =
      "-Dtest.pipes=host1|host2|host3\n-Dtest.wildcards=path/*/pattern\n-Dtest.ampersand=value&other",
    windowsSupport = false,
  )("-v"): (out: List[String]) =>
    // Verify that properties with special characters are handled correctly
    // The pipe characters should be treated literally, not as shell operators
    assert(
      out.contains[String]("-Dtest.pipes=host1|host2|host3"),
      "Property with pipes should be handled correctly"
    )
    assert(
      out.contains[String]("-Dtest.wildcards=path/*/pattern"),
      "Property with wildcards should be handled correctly"
    )
    assert(
      out.contains[String]("-Dtest.ampersand=value&other"),
      "Property with ampersands should be handled correctly"
    )
    // Verify no shell errors occurred (no "command not found" messages or "unexpected" errors)
    val errorMessages = out.filter(line =>
      line.contains("command not found") ||
        line.contains("was unexpected at this time") ||
        line.contains("syntax error")
    )
    assert(
      errorMessages.isEmpty,
      s"Should not have shell expansion errors, but found: ${errorMessages.mkString(", ")}"
    )

  // Test for issue #8755: Inline comments should be supported in .jvmopts
  testOutput(
    "sbt with inline comments in .jvmopts",
    jvmoptsFileContents =
      "--add-opens=java.base/java.util=ALL-UNNAMED # This is an inline comment\n-Dtest.key=value # Another comment",
    windowsSupport = false,
  )("-v"): (out: List[String]) =>
    // Verify that options are present (comments should be stripped)
    assert(
      out.contains[String]("--add-opens=java.base/java.util=ALL-UNNAMED"),
      "Option with inline comment should be parsed correctly"
    )
    assert(
      out.contains[String]("-Dtest.key=value"),
      "System property with inline comment should be parsed correctly"
    )
    // Verify comments themselves are NOT present as separate arguments
    assert(
      !out.exists(_.contains("This is an inline comment")),
      "Inline comment should not appear in command line"
    )
    assert(
      !out.exists(_.contains("Another comment")),
      "Inline comment should not appear in command line"
    )
    // Verify no "command not found" errors for '#' character
    val errorMessages = out.filter(line =>
      line.contains("Could not find or load main class #") ||
        line.contains("command not found") ||
        line.contains("was unexpected")
    )
    assert(
      errorMessages.isEmpty,
      s"Should not have errors from comment character, but found: ${errorMessages.mkString(", ")}"
    )

end RunnerScriptTest
