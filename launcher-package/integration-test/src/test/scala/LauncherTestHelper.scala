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
  def launcherCommand(scriptPath: String): Seq[String] =
    if (useSbtw)
      Seq("java", "-cp", System.getProperty("java.class.path"), "sbtw.Main")
    else
      Seq(scriptPath)
}
