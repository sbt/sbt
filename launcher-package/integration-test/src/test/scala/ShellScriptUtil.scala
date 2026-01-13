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

      try
        val sbtOptsFile = new File(workingDirectory, ".sbtopts")
        sbtOptsFile.createNewFile()
        val writer = new PrintWriter(sbtOptsFile)
        try {
          writer.write(sbtOptsFileContents)
        } finally {
          writer.close()
        }

        var sbtHome: Option[File] = None
        var configHome: Option[File] = None
        val envVars = scala.collection.mutable.Map[String, String]()

        // Set up dist sbtopts if provided
        if (distSbtoptsContents.nonEmpty) {
          val sbtHomeDir = Files.createTempDirectory("sbt-home").toFile
          sbtHome = Some(sbtHomeDir)
          val distSbtoptsDir = new File(sbtHomeDir, "conf")
          distSbtoptsDir.mkdirs()
          val distSbtoptsFile = new File(distSbtoptsDir, "sbtopts")
          IO.write(distSbtoptsFile, distSbtoptsContents)
          envVars("SBT_HOME") = sbtHomeDir.getAbsolutePath
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
            envVars.toSeq: _*
          )
          .!!
          .linesIterator
          .toList
        f(out)
        ()
      finally
        IO.delete(workingDirectory)
        sbtHome.foreach(IO.delete)
        configHome.foreach(IO.delete)
    }
}
