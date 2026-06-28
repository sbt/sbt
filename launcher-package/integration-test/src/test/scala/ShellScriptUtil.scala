package example.test

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import sbt.io.IO
import scala.collection.mutable
import scala.sys.process.{ BasicIO, Process, ProcessIO }
import verify.BasicTestSuite

trait ShellScriptUtil extends BasicTestSuite {
  val isWindows: Boolean =
    sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")

  protected val javaBinDir = new File("bin").getAbsolutePath

  protected def retry[A1](f: () => A1, maxAttempt: Int = 10): A1 =
    try {
      f()
    } catch {
      case e: Exception if maxAttempt > 1 =>
        Thread.sleep(100)
        retry(f, maxAttempt - 1)
      case e: Exception => throw e
    }

  def isGitBashTest: Boolean = false
  lazy val sbtScript = IntegrationTestPaths.sbtScript(isWindows && !isGitBashTest)

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
      buildPropsContents: String = "",
      stagedRunnerVersionOverride: String = "",
      windowsSupport: Boolean = true,
      citestVariant: String = "citest",
  )(args: String*)(f: List[String] => Any) =
    if isGitBashTest && !isWindows then
      test("gitbash: " + name):
        cancel("skip")
    else if !isGitBashTest && !windowsSupport && isWindows then
      test(name):
        cancel("test not supported on Windows")
    else
      test(if isGitBashTest then "gitbash: " + name else name) {
        val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
        val citestDir = IntegrationTestPaths.citestDir(citestVariant)
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

          if (buildPropsContents.nonEmpty) {
            val projectDir = new File(workingDirectory, "project")
            projectDir.mkdirs()
            IO.write(new File(projectDir, "build.properties"), buildPropsContents)
          }

          val envVars = scala.collection.mutable.Map[String, String]()

          // Set up dist sbtopts if provided
          // Note: sbt script derives sbt_home from script location, not SBT_HOME env var
          // Copy the sbt staging directory to a temp location to avoid modifying the staging directory
          if (distSbtoptsContents.nonEmpty || stagedRunnerVersionOverride.nonEmpty) {
            val originalSbtHome = sbtScript.getParentFile.getParentFile
            val tempSbtHomeDir = Files.createTempDirectory("sbt-home-test").toFile
            tempSbtHome = Some(tempSbtHomeDir)
            // Copy the entire sbt home directory structure
            retry(() => IO.copyDirectory(originalSbtHome, tempSbtHomeDir))
            // Get the script from the copied directory
            val binDir = new File(tempSbtHomeDir, "bin")
            testSbtScript = new File(binDir, sbtScript.getName)
            if (distSbtoptsContents.nonEmpty) {
              // Create dist sbtopts in the copied directory
              val distSbtoptsDir = new File(tempSbtHomeDir, "conf")
              distSbtoptsDir.mkdirs()
              IO.write(new File(distSbtoptsDir, "sbtopts"), distSbtoptsContents)
            }
            if (stagedRunnerVersionOverride.nonEmpty) {
              val isBat = testSbtScript.getName.endsWith(".bat")
              val prefix =
                if (isBat) "set init_sbt_version=" else "declare init_sbt_version="
              val pattern =
                if (isBat) "(?m)^set init_sbt_version=.*$"
                else "(?m)^declare init_sbt_version=.*$"
              val original = IO.read(testSbtScript)
              val regex = pattern.r
              assert(
                regex.findFirstIn(original).nonEmpty,
                s"init_sbt_version line not found in $testSbtScript"
              )
              val replacement =
                java.util.regex.Matcher.quoteReplacement(prefix + stagedRunnerVersionOverride)
              val updated = regex.replaceAllIn(original, replacement)
              assert(updated.contains(prefix + stagedRunnerVersionOverride))
              IO.write(testSbtScript, updated)
              if (!isBat) testSbtScript.setExecutable(true)
            }
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

          val path = sys.env.getOrElse("PATH", sys.env.getOrElse("Path", ""))
          val javaHomeEnv = sys.env.getOrElse("JAVA_HOME", System.getProperty("java.home"))
          envVars("JAVA_OPTS") = javaOpts
          envVars("SBT_OPTS") = sbtOpts
          envVars("JAVA_TOOL_OPTIONS") = javaToolOptions
          if isWindows then
            envVars("JAVACMD") = new File(javaBinDir, "java").getAbsolutePath()
            envVars("JAVA_HOME") = javaHomeEnv
          else
            envVars("PATH") = javaBinDir + File.pathSeparator + path
            envVars("JAVA_HOME") = javaHomeEnv
          val cmd =
            LauncherTestHelper.launcherCommand(testSbtScript.getAbsolutePath, isGitBashTest) ++ args
          val lines = mutable.ListBuffer.empty[String]
          def processLine(line: String): Unit =
            Console.err.println(line)
            lines.append(line)
          val p = Process(cmd, workingDirectory, envVars.toSeq*)
            .run(
              new ProcessIO(
                _.close(),
                BasicIO.processFully(processLine),
                BasicIO.processFully(processLine)
              )
            )
          if p.exitValue() != 0 then
            lines.foreach(l => Console.err.println(l))
            sys.error(s"process exit with ${p.exitValue()}")
          f(lines.toList)
          ()
        finally
          IO.delete(workingDirectory)
          // Clean up temporary sbt home directory if we created one
          tempSbtHome.foreach(IO.delete)
          configHome.foreach(IO.delete)
      }
}
