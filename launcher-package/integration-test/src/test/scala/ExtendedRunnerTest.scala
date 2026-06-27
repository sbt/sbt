package example.test

import scala.sys.process.*
import java.io.File
import java.util.Locale
import sbt.io.IO
import verify.BasicTestSuite

object ExtendedRunnerTest extends BasicTestSuite:
  // 1.3.0, 1.3.0-M4
  private[test] val versionRegEx = "\\d(\\.\\d+){2}(-\\w+)?"

  lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  lazy val isMac: Boolean = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("mac")
  lazy val sbtScript = IntegrationTestPaths.sbtScript(isWindows)

  private def launcherCmd = LauncherTestHelper.launcherCommand(sbtScript.getAbsolutePath)

  def sbtProcess(args: String*) = sbtProcessWithOpts(args*)("", "")
  def sbtProcessWithOpts(args: String*)(javaOpts: String, sbtOpts: String) =
    Process(
      launcherCmd ++ args,
      IntegrationTestPaths.citestDir("citest"),
      "JAVA_OPTS" -> javaOpts,
      "SBT_OPTS" -> sbtOpts
    )
  def sbtProcessInDir(dir: File)(args: String*) =
    Process(
      launcherCmd ++ args,
      dir,
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> ""
    )
  def sbtProcessLikeBsd(args: String*) =
    Process(
      launcherCmd ++ args,
      IntegrationTestPaths.citestDir("citest"),
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> "",
      "OSTYPE" -> "openbsd7.9"
    )

  test("sbt runs") {
    assert(sbtScript.exists)
    val out = sbtProcess("compile", "-v").!
    assert(out == 0)
    ()
  }

  def testVersion(lines: List[String]): Unit = {
    assert(lines.size >= 2)
    val expected0 = s"(?m)^sbt version in this project: $versionRegEx(\\r)?"
    assert(lines(0).matches(expected0))
    val expected1 = s"sbt runner version: $versionRegEx$$"
    assert(lines(1).matches(expected1))
  }

  /* TODO: The lines seems to return List([0Jsbt runner version: 1.11.4) on CI
  test("sbt -V|-version|--version should print sbtVersion") {
    val out = sbtProcess("-version").!!.trim
    testVersion(out.linesIterator.toList)

    val out2 = sbtProcess("--version").!!.trim
    testVersion(out2.linesIterator.toList)

    val out3 = sbtProcess("-V").!!.trim
    testVersion(out3.linesIterator.toList)
  }
   */

  test("sbt -V in empty directory") {
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("-V").!!.trim
      val expectedVersion = "^" + versionRegEx + "$"
      val targetDir = new File(tmp, "target")
      assert(!targetDir.exists, "expected target directory to not exist, but existed")
    }
    ()
  }

  /* TODO: Not sure why but the output is returning [0J on CI
  test("sbt --numeric-version should print sbt script version") {
    val out = sbtProcess("--numeric-version").!!.trim
    val expectedVersion = "^"+versionRegEx+"$"
    assert(out.matches(expectedVersion))
    ()
  }
   */

  test("sbt --sbt-jar should run") {
    val out = sbtProcess(
      "compile",
      "-v",
      "--sbt-jar",
      "../target/universal/stage/bin/sbt-launch.jar"
    ).!!.linesIterator.toList
    assert(
      out.contains[String]("../target/universal/stage/bin/sbt-launch.jar") ||
        out.contains[String]("\"../target/universal/stage/bin/sbt-launch.jar\"")
    )
    ()
  }

  test("sbt \"testOnly *\"") {
    if (isMac) ()
    else {
      val out = sbtProcess("testOnly *", "--no-colors", "-v").!!.linesIterator.toList
      assert(out.contains[String]("[info] HelloTest"))
      ()
    }
  }

  test("sbt in empty directory") {
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("about").!
      assert(out == 1)
    }
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("about", "--allow-empty").!
      assert(out == 0)
    }
    ()
  }

  test("sbt --script-version in empty directory") {
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("--script-version").!!.trim
      val expectedVersion = "^" + versionRegEx + "$"
      assert(out.matches(expectedVersion))
    }
    ()
  }

  test("sbt --jvm-client") {
    if (isMac) {
      // `--jvm-client` is flaky in macOS CI due to intermittent startup/connection failures.
      // Keep coverage on Linux/Windows where the behavior is stable.
      ()
    } else {
      val out = sbtProcess("--jvm-client", "--no-colors", "compile").!!.linesIterator.toList
      if (isWindows) {
        println(out)
      } else {
        assert(out.exists { _.contains("server was not detected") })
      }
      val out2 = sbtProcess("--jvm-client", "--no-colors", "shutdown").!!.linesIterator.toList
      if (isWindows) {
        println(out2)
      } else {
        assert(out2.exists { _.contains("disconnected") })
      }
    }
    ()
  }

  test("sbt falls back to JVM client on unsupported platform") {
    if isWindows || isMac then ()
    else
      val out = sbtProcessLikeBsd("--client", "--no-colors", "compile").!!.linesIterator.toList
      assert(out.exists { _.contains("server was not detected") })
      sbtProcessLikeBsd("--client", "--no-colors", "shutdown").!
    ()
  }

  // Test for issue #6485: Test `sbt --client` startup
  // https://github.com/sbt/sbt/issues/6485
  test("sbt --client startup time") {
    if (isWindows || isMac) {
      // Skip on Windows (sbtn behavior differs) and macOS CI (slow hostname resolution)
      ()
    } else {
      // First call starts the server if not running (warmup)
      val warmup = sbtProcess("--client", "version").!
      assert(warmup == 0, "Warmup sbt --client version failed")

      // Measure startup time for sbt --client when server is already running
      // Run multiple times and take the average to reduce variance
      val iterations = 5
      val times = (1 to iterations).map { _ =>
        val start = System.nanoTime()
        val exitCode = sbtProcess("--client", "version").!
        val elapsed = (System.nanoTime() - start) / 1_000_000 // Convert to milliseconds
        assert(exitCode == 0, "sbt --client version failed")
        elapsed
      }

      val avgTime = times.sum / iterations
      val maxTime = times.max

      println(s"sbt --client startup times (ms): ${times.mkString(", ")}")
      println(s"Average: ${avgTime}ms, Max: ${maxTime}ms")

      // Cap at 2000ms to catch significant regressions while allowing for CI variance.
      // The original issue #5980 mentioned ~200ms on developer machines in 2021,
      // but CI runners are typically 2-3x slower than local development machines.
      assert(
        avgTime < 2000,
        s"sbt --client startup time (${avgTime}ms average) exceeded 2000ms threshold"
      )

      // Cleanup: shutdown the server
      val shutdown = sbtProcess("--client", "shutdown").!
      assert(shutdown == 0, "Failed to shutdown sbt server")
    }
    ()
  }

  // Test for issue #8644: sbt.bat fails when project path contains parentheses
  // https://github.com/sbt/sbt/issues/8644
  test("sbt.bat handles paths with parentheses") {
    if (!isWindows) {
      // This test is Windows-specific, skip on other platforms
      ()
    } else {
      IO.withTemporaryDirectory { baseDir =>
        // Create a temporary directory with parentheses in the name
        val testDir = new File(baseDir, "test(parentheses)")

        // Create the directory structure
        IO.createDirectory(testDir)
        val projectDir = new File(testDir, "project")
        IO.createDirectory(projectDir)

        // Create a minimal build.properties to make it a valid sbt project
        val buildProps = new File(projectDir, "build.properties")
        IO.write(buildProps, "sbt.version=1.12.1\n")

        // Test 1: Run sbt from directory with parentheses - should work without parsing errors
        val out1 = sbtProcessInDir(testDir)("--script-version").!!.trim
        val expectedVersion = "^" + versionRegEx + "$"
        assert(out1.matches(expectedVersion), s"Expected version format, got: $out1")

        // Test 2: Test error message when no build.sbt exists (this is where the fix is most visible)
        // Create a directory with parentheses but no build.sbt
        val emptyDir = new File(baseDir, "empty(parentheses)")
        IO.createDirectory(emptyDir)

        // Run sbt from empty directory - should fail gracefully with proper error message
        // Use ProcessLogger to capture stderr without throwing on non-zero exit
        import scala.sys.process.ProcessLogger
        val errorBuffer = new StringBuilder
        val logger = ProcessLogger(
          _ => (), // ignore stdout
          line => errorBuffer.append(line).append("\n") // capture stderr
        )
        val exitCode = sbtProcessInDir(emptyDir)("compile").!(logger)
        assert(exitCode == 1, "Expected sbt to fail when no build.sbt exists")

        // Verify the error output doesn't contain ") was unexpected" parsing error
        val errorOutput = errorBuffer.toString
        val hasParsingError = errorOutput.contains(") was unexpected")
        assert(
          !hasParsingError,
          s"Error message should not contain parsing error when path has parentheses. Error output: $errorOutput"
        )
      }
    }
    ()
  }
end ExtendedRunnerTest
