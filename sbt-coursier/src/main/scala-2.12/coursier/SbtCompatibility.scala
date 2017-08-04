package coursier

object SbtCompatibility {

  val ConfigRef = sbt.librarymanagement.ConfigRef
  type ConfigRef = sbt.librarymanagement.ConfigRef

  val GetClassifiersModule = sbt.librarymanagement.GetClassifiersModule
  type GetClassifiersModule = sbt.librarymanagement.GetClassifiersModule

  object SbtPomExtraProperties {
    def POM_INFO_KEY_PREFIX = sbt.internal.librarymanagement.mavenint.SbtPomExtraProperties.POM_INFO_KEY_PREFIX
  }

  type MavenRepository = sbt.librarymanagement.MavenRepository

  type IvySbt = sbt.internal.librarymanagement.IvySbt

  type Binary = sbt.librarymanagement.Binary
  type Disabled = sbt.librarymanagement.Disabled
  type Full = sbt.librarymanagement.Full

  implicit class BinaryOps(private val binary: Binary) extends AnyVal {
    def remapVersion(scalaBinaryVersion: String): String =
      binary.prefix + scalaBinaryVersion + binary.suffix
  }

  implicit class FullOps(private val full: Full) extends AnyVal {
    def remapVersion(scalaVersion: String): String =
      full.prefix + scalaVersion + full.suffix
  }

  def needsIvyXmlLocal = sbt.Keys.publishLocalConfiguration
  def needsIvyXml = sbt.Keys.publishConfiguration

}
