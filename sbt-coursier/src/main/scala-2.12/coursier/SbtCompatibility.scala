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

  def needsIvyXmlLocal = sbt.Keys.publishLocalConfiguration
  def needsIvyXml = sbt.Keys.publishConfiguration

}
