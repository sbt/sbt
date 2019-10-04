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
        Seq(scalacheck % Test, specs2 % Test, junit % Test, scalatest % Test, hedgehog % Test)
      else Seq()
    )
  }
  import autoImport._

  override def buildSettings: Seq[Setting[_]] = Seq(
    includeTestDependencies := true
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    crossVersion in update := CrossVersion.full,
    resolvers += Resolver.typesafeIvyRepo("releases").withName("typesafe-alt-project-releases")
  )
}
