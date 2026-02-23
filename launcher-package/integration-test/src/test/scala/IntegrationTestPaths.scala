package example.test

import java.io.File

/**
 * Resolves paths for launcher-package integration tests relative to the
 * integration-test project base directory. The build injects
 * sbt.test.integrationtest.basedir when Test/fork is true so paths are
 * correct regardless of the forked JVM's working directory (e.g. on Windows).
 */
object IntegrationTestPaths {
  private val baseDir: Option[File] =
    sys.props.get("sbt.test.integrationtest.basedir").map(new File(_))

  def sbtScript(isWindows: Boolean): File =
    baseDir match {
      case Some(b) =>
        val name = if (isWindows) "sbt.bat" else "sbt"
        new File(b.getParentFile, s"target/universal/stage/bin/$name").getAbsoluteFile
      case None =>
        val rel =
          if (isWindows) "../target/universal/stage/bin/sbt.bat"
          else "../target/universal/stage/bin/sbt"
        new File(rel).getAbsoluteFile
    }

  def citestDir(citestVariant: String = "citest"): File =
    baseDir match {
      case Some(b) =>
        new File(b.getParentFile, citestVariant).getAbsoluteFile
      case None =>
        new File("..", citestVariant).getAbsoluteFile
    }
}
