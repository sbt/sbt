package example.test

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import sbt.io.IO
import verify.BasicTestSuite

trait ShellScriptUtil extends BasicTestSuite {
  val isWindows: Boolean =
    sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")

  private val javaBinDir = new File("launcher-package/integration-test/bin").getAbsolutePath

  private def retry[A1](f: () => A1, maxAttempt: Int = 10): A1 =
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
      javaToolOptions: String = ""
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
        val path = sys.env.getOrElse("PATH", sys.env("Path"))
        val out = scala.sys.process
          .Process(
            Seq(sbtScript.getAbsolutePath) ++ args,
            workingDirectory,
            "JAVA_OPTS" -> javaOpts,
            "SBT_OPTS" -> sbtOpts,
            "JAVA_TOOL_OPTIONS" -> javaToolOptions,
            if (isWindows)
              "JAVACMD" -> new File(javaBinDir, "java").getAbsolutePath()
            else
              "PATH" -> (javaBinDir + File.pathSeparator + path)
          )
          .!!
          .linesIterator
          .toList
        f(out)
        ()
      finally IO.delete(workingDirectory)
    }
}
