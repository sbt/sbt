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
      val v = scalaVersion.value
      v.startsWith("2.10.") || v.startsWith("2.11.") || v.startsWith("2.12.")
    }
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    crossVersion in update := {
      scalaVersion.value match {
        case sv if sv startsWith "2.8."  => crossVersion.value
        case sv if sv startsWith "2.9."  => crossVersion.value
        case sv if sv startsWith "2.10." => crossVersion.value
        case sv if sv startsWith "2.11." => CrossVersion.full
        case sv if sv startsWith "2.12." => CrossVersion.full
      }
    },
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
}
