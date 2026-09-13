package sbt.internal.librarymanagement
package mavenint

/**
 * Keys for the extra pom properties used to propagate the sbtVersion/scalaVersion
 * attributes for sbt plugin dependencies, plus the Maven packagings that sbt treats as jars.
 */
object PomExtraAttributeKeys:
  val SbtVersionKey = "sbtVersion"
  val ScalaVersionKey = "scalaVersion"

  // packagings that should be jars, but that Ivy doesn't handle as jars
  val JarPackagings = Set("eclipse-plugin", "hk2-jar", "orbit", "scala-jar")
end PomExtraAttributeKeys
