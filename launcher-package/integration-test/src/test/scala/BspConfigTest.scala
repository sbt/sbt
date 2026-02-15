package example.test

import scala.sys.process.*
import java.io.File
import java.util.Locale
import sbt.io.IO
import verify.BasicTestSuite

// Test for issues #7792/#7794: BSP config generation and argv execution
object BspConfigTest extends BasicTestSuite:
  lazy val isWindows: Boolean =
    sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  lazy val sbtScript = IntegrationTestPaths.sbtScript(isWindows)

  private def launcherCmd = LauncherTestHelper.launcherCommand(sbtScript.getAbsolutePath)

  def sbtProcessInDir(dir: File)(args: String*) =
    Process(
      launcherCmd ++ args,
      dir,
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> ""
    )

  test("sbt bspConfig") {
    import ujson.*

    IO.withTemporaryDirectory { tmp =>
      // Create minimal build.sbt for the test project
      IO.write(new File(tmp, "build.sbt"), """name := "test-bsp-config"""")

      // Run bspConfig to generate .bsp/sbt.json
      val configResult = sbtProcessInDir(tmp)("bspConfig", "--batch").!
      assert(configResult == 0, s"bspConfig command failed with exit code $configResult")

      // Verify .bsp/sbt.json exists
      val bspFile = new File(tmp, ".bsp/sbt.json")
      assert(bspFile.exists, ".bsp/sbt.json should exist after running bspConfig")

      // Parse and verify JSON content
      val content = IO.read(bspFile)
      val json = ujson.read(content)

      // Extract argv array from JSON
      val argvValue = json.obj.get("argv")
      assert(argvValue.isDefined, "argv field not found in sbt.json")

      val argv = argvValue.get.arr.map(_.str).toVector

      // Verify argv structure
      assert(argv.nonEmpty, "argv should not be empty")
      assert(argv.head.contains("java"), s"argv should start with java command, got: ${argv.head}")
      assert(argv.contains("-bsp"), s"argv should contain -bsp flag, got: $argv")

      // Test execution of the generated argv
      // Run the BSP command with a very short timeout to verify it starts correctly
      // We just need to verify the command doesn't fail immediately on startup
      if (!isWindows) {
        // On Unix, we can test the argv execution
        // Create a process and check if it starts (will timeout waiting for BSP input)
        val process = Process(argv.toSeq, tmp)
        val processBuilder = process.run(ProcessLogger(_ => (), _ => ()))

        // Give it a moment to fail if it's going to fail immediately
        Thread.sleep(500)

        // If still running, it means the BSP server started successfully
        val isAlive = processBuilder.isAlive()
        processBuilder.destroy()

        // The process should either still be alive (waiting for BSP messages)
        // or have exited with code 0 (graceful)
        if (!isAlive) {
          val exitCode = processBuilder.exitValue()
          assert(
            exitCode == 0 || exitCode == 143, // 143 = SIGTERM from destroy()
            s"BSP process failed with exit code $exitCode"
          )
        }
      }
    }
    ()
  }
end BspConfigTest
