import sbt._
import Keys._
import Dependencies._

object NightlyPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport {
    val includeTestDependencies = settingKey[Boolean]("Doesn't declare test dependencies.")

    def testDependencies = libraryDependencies ++= (
      if (includeTestDependencies.value)
        Seq(scalaCheck % Test, specs2 % Test, junit % Test, scalatest % Test)
      else Seq()
    )
  }
  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    // Avoid 2.9.x precompiled
    // Avoid 2.8.x precompiled
    includeTestDependencies := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 10 => true
        case _                       => false
      }
    }
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    crossVersion in update := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n >= 11 => CrossVersion.full
        case _                       => crossVersion.value
      }
    },
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
}
