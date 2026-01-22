package example.test

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import sbt.io.IO
import verify.BasicTestSuite

trait ShellScriptUtil extends BasicTestSuite {
  val isWindows: Boolean =
    sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")

  protected val javaBinDir = new File("launcher-package/integration-test/bin").getAbsolutePath

  protected def retry[A1](f: () => A1, maxAttempt: Int = 10): A1 =
    try {
      f()
    } catch {
      case e: Exception if maxAttempt > 1 =>
        Thread.sleep(100)
        retry(f, maxAttempt - 1)
      case e: Exception => throw e
    }

  val sbtScript =
    if (isWindows) new File("launcher-package/target/universal/stage/bin/sbt.bat")
    else new File("launcher-package/target/universal/stage/bin/sbt")

  /**
   * testOutput is a helper function to create a test for shell script.
   */
  inline def testOutput(
      name: String,
      javaOpts: String = "",
      sbtOpts: String = "",
      sbtOptsFileContents: String = "",
      javaToolOptions: String = "",
      distSbtoptsContents: String = "",
      machineSbtoptsContents: String = "",
      jvmoptsFileContents: String = "",
      windowsSupport: Boolean = true,
  )(args: String*)(f: List[String] => Any) =
    if !windowsSupport && isWindows then
      test(name):
        cancel("test not supported on Windows")
    else
      test(name) {
        val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
        val citestDir = new File("launcher-package/citest")
        // Clean target directory if it exists to avoid copying temporary files that may be deleted during copy
        val targetDir = new File(citestDir, "target")
        if (targetDir.exists()) {
          try {
            IO.delete(targetDir)
          } catch {
            case _: Exception => // Ignore deletion errors, will retry copy
          }
        }
        // Retry copy operation to handle race conditions with temporary files
        retry(() => {
          try {
            IO.copyDirectory(citestDir, workingDirectory)
          } catch {
            case e: java.io.IOException if e.getMessage.contains("does not exist") =>
              // If a file doesn't exist during copy, clean target and retry
              val targetInCitest = new File(citestDir, "target")
              if (targetInCitest.exists()) {
                try {
                  IO.delete(targetInCitest)
                } catch {
                  case _: Exception => // Ignore
                }
              }
              throw e // Re-throw to trigger retry
          }
        })

        var sbtHome: Option[File] = None
        var configHome: Option[File] = None
        var tempSbtHome: Option[File] = None
        var testSbtScript: File = sbtScript
        try
          val sbtOptsFile = new File(workingDirectory, ".sbtopts")
          sbtOptsFile.createNewFile()
          val writer = new PrintWriter(sbtOptsFile)
          try {
            writer.write(sbtOptsFileContents)
          } finally {
            writer.close()
          }

          // Create .jvmopts file if contents provided
          if (jvmoptsFileContents.nonEmpty) {
            val jvmoptsFile = new File(workingDirectory, ".jvmopts")
            jvmoptsFile.createNewFile()
            val jvmoptsWriter = new PrintWriter(jvmoptsFile)
            try {
              jvmoptsWriter.write(jvmoptsFileContents)
            } finally {
              jvmoptsWriter.close()
            }
          }

          val envVars = scala.collection.mutable.Map[String, String]()

          // Set up dist sbtopts if provided
          // Note: sbt script derives sbt_home from script location, not SBT_HOME env var
          // Copy the sbt staging directory to a temp location to avoid modifying the staging directory
          if (distSbtoptsContents.nonEmpty) {
            val originalSbtHome = sbtScript.getParentFile.getParentFile
            val tempSbtHomeDir = Files.createTempDirectory("sbt-home-test").toFile
            tempSbtHome = Some(tempSbtHomeDir)
            // Copy the entire sbt home directory structure
            retry(() => IO.copyDirectory(originalSbtHome, tempSbtHomeDir))
            // Get the script from the copied directory
            val binDir = new File(tempSbtHomeDir, "bin")
            testSbtScript = new File(binDir, sbtScript.getName)
            // Create dist sbtopts in the copied directory
            val distSbtoptsDir = new File(tempSbtHomeDir, "conf")
            distSbtoptsDir.mkdirs()
            val distSbtoptsFile = new File(distSbtoptsDir, "sbtopts")
            IO.write(distSbtoptsFile, distSbtoptsContents)
            // Store reference for cleanup
            sbtHome = Some(tempSbtHomeDir)
          }

          // Ensure no machine sbtopts exists when testing dist-only (unless explicitly provided)
          // The script only loads dist if machine doesn't exist
          if (
            distSbtoptsContents.nonEmpty && machineSbtoptsContents.isEmpty && configHome.isEmpty
          ) {
            // Set XDG_CONFIG_HOME to a temp directory without sbtopts to prevent default machine sbtopts from being found
            val emptyConfigHome = Files.createTempDirectory("empty-config-home").toFile
            envVars("XDG_CONFIG_HOME") = emptyConfigHome.getAbsolutePath
            // Also unset SBT_ETC_FILE if it exists
            sys.env.get("SBT_ETC_FILE").foreach(_ => envVars("SBT_ETC_FILE") = "")
            // Store for cleanup
            configHome = Some(emptyConfigHome)
          }

          // Set up machine sbtopts if provided
          if (machineSbtoptsContents.nonEmpty) {
            val configHomeDir = Files.createTempDirectory("config-home").toFile
            configHome = Some(configHomeDir)
            val machineSbtoptsDir = new File(configHomeDir, "sbt")
            machineSbtoptsDir.mkdirs()
            val machineSbtoptsFile = new File(machineSbtoptsDir, "sbtopts")
            IO.write(machineSbtoptsFile, machineSbtoptsContents)
            envVars("XDG_CONFIG_HOME") = configHomeDir.getAbsolutePath
          }

          val path = sys.env.getOrElse("PATH", sys.env("Path"))
          envVars("JAVA_OPTS") = javaOpts
          envVars("SBT_OPTS") = sbtOpts
          envVars("JAVA_TOOL_OPTIONS") = javaToolOptions
          if (isWindows)
            envVars("JAVACMD") = new File(javaBinDir, "java").getAbsolutePath()
          else
            envVars("PATH") = javaBinDir + File.pathSeparator + path

          val out = scala.sys.process
            .Process(
              Seq(testSbtScript.getAbsolutePath) ++ args,
              workingDirectory,
              envVars.toSeq*
            )
            .!!
            .linesIterator
            .toList
          f(out)
          ()
        finally
          IO.delete(workingDirectory)
          // Clean up temporary sbt home directory if we created one
          tempSbtHome.foreach(IO.delete)
          configHome.foreach(IO.delete)
      }
}
