package lmcoursier.internal

import java.io.File

import coursier.core.{Module, ModuleName, Organization}

// private[coursier]
object SbtBootJars {
  def apply(
    scalaOrg: Organization,
    scalaVersion: String,
    jars: Seq[File]
  ): Map[(Module, String), File] =
    jars
      .collect {
        case jar if jar.getName.endsWith(".jar") =>
          val name = ModuleName(jar.getName.stripSuffix(".jar"))
          val mod = Module(scalaOrg, name, Map.empty)

          (mod, scalaVersion) -> jar
      }
      .toMap
}