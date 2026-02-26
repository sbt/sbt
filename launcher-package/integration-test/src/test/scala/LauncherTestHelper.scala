package example.test

import java.util.Locale

/**
 * Shared helper for launcher integration tests. When sbt.test.useSbtw=true on Windows,
 * tests use sbtw (JVM) as the runner instead of sbt.bat, to validate sbtw as a drop-in.
 */
object LauncherTestHelper {
  def isWindows: Boolean =
    sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("windows")

  def useSbtw: Boolean =
    isWindows && sys.props.get("sbt.test.useSbtw").contains("true")

  /** Command prefix to run the launcher: either script path or java -cp sbtw.Main */
  def launcherCommand(scriptPath: String, useGitBash: Boolean = false): Seq[String] =
    if useGitBash then
      Seq("C:\\Program Files\\Git\\bin\\bash.EXE", "--noprofile", "-e", "--", scriptPath)
    else if useSbtw then
      val cp = sys.props.get("sbt.test.classpath").getOrElse(System.getProperty("java.class.path"))
      Seq("java", "-cp", cp, "sbtw.Main")
    else Seq(scriptPath)
}
