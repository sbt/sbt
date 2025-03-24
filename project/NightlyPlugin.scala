import sbt.*
import Keys.*
import Dependencies.*

object NightlyPlugin extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  object autoImport {
    val includeTestDependencies = settingKey[Boolean]("Doesn't declare test dependencies.")

    def testDependencies = libraryDependencies ++= (
      if (includeTestDependencies.value)
        Seq(
          scalacheck % Test,
          junit % Test,
          scalatest % Test,
          scalaVerify % Test,
          hedgehog % Test
        )
      else Seq()
    )
  }
  import autoImport.*

  override def buildSettings: Seq[Setting[_]] = Seq(
    includeTestDependencies := true
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    resolvers += Resolver.typesafeIvyRepo("releases").withName("typesafe-alt-project-releases")
  )
}
