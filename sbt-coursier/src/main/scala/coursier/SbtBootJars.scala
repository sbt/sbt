package coursier

import java.io.File

object SbtBootJars {
  def apply(
    scalaOrg: String,
    scalaVersion: String,
    jars: Seq[File]
  ): Map[(Module, String), File] =
    jars
      .collect {
        case jar if jar.getName.endsWith(".jar") =>
          val name = jar.getName.stripSuffix(".jar")
          val mod = Module(scalaOrg, name)

          (mod, scalaVersion) -> jar
      }
      .toMap
}