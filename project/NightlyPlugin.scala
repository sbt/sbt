import sbt._
import Keys._
import Dependencies._

object NightlyPlugin extends AutoPlugin {
  import autoImport._

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin
  object autoImport {
    lazy val nightly212 = SettingKey[Boolean]("nightly212")
    lazy val includeTestDependencies = SettingKey[Boolean]("includeTestDependencies", "Doesn't declare test dependencies.")

    def testDependencies = libraryDependencies <++= includeTestDependencies { incl =>
      if (incl) Seq(
        scalaCheck % Test,
        specs2 % Test,
        junit % Test
      )
      else Seq()
    }
  }

  override def buildSettings: Seq[Setting[_]] = Seq(
    nightly212 <<= scalaVersion(v => v.startsWith("2.12.")),
    includeTestDependencies <<= nightly212(x => !x)
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    crossVersion in update := {
      scalaVersion.value match {
        case sv if sv startsWith "2.8." => crossVersion.value
        case sv if sv startsWith "2.9." => crossVersion.value
        case sv if sv startsWith "2.10." => crossVersion.value
        case sv if sv startsWith "2.11." => CrossVersion.full
        case sv if sv startsWith "2.12." => CrossVersion.full
      }
    },
    resolvers += Resolver.typesafeIvyRepo("releases")
  )
}
