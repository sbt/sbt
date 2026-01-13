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
      case _ if maxAttempt <= 1 =>
        Thread.sleep(100)
        retry(f, maxAttempt - 1)
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
      machineSbtoptsContents: String = ""
  )(args: String*)(f: List[String] => Any) =
    test(name) {
      val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
      retry(() => IO.copyDirectory(new File("launcher-package/citest"), workingDirectory))

      var sbtHome: Option[File] = None
      var configHome: Option[File] = None
      try
        val sbtOptsFile = new File(workingDirectory, ".sbtopts")
        sbtOptsFile.createNewFile()
        val writer = new PrintWriter(sbtOptsFile)
        try {
          writer.write(sbtOptsFileContents)
        } finally {
          writer.close()
        }

        val envVars = scala.collection.mutable.Map[String, String]()

        // Set up dist sbtopts if provided
        // Note: sbt script derives sbt_home from script location, not SBT_HOME env var
        // So we need to create the dist sbtopts in the actual sbt_home location
        if (distSbtoptsContents.nonEmpty) {
          // sbt_home is the parent of the bin directory containing the script
          val sbtHomeDir = sbtScript.getParentFile.getParentFile
          val distSbtoptsDir = new File(sbtHomeDir, "conf")
          distSbtoptsDir.mkdirs()
          val distSbtoptsFile = new File(distSbtoptsDir, "sbtopts")
          // Ensure the file is created with proper content
          IO.write(distSbtoptsFile, distSbtoptsContents)
          // Store reference for cleanup
          sbtHome = Some(sbtHomeDir)
        }

        // Ensure no machine sbtopts exists when testing dist-only (unless explicitly provided)
        // The script only loads dist if machine doesn't exist
        if (distSbtoptsContents.nonEmpty && machineSbtoptsContents.isEmpty && configHome.isEmpty) {
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
            Seq(sbtScript.getAbsolutePath) ++ args,
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
        // Restore original dist sbtopts if we modified it
        sbtHome.foreach { home =>
          val distSbtoptsFile = new File(new File(home, "conf"), "sbtopts")
          if (distSbtoptsFile.exists() && distSbtoptsContents.nonEmpty) {
            // We created this file for testing, so delete it
            distSbtoptsFile.delete()
          }
        }
        configHome.foreach(IO.delete)
    }
}
