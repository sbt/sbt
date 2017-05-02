package coursier

object SbtCompatibility {

  val GetClassifiersModule = sbt.internal.librarymanagement.GetClassifiersModule
  type GetClassifiersModule = sbt.internal.librarymanagement.GetClassifiersModule

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
      scalaBinaryVersion
  }

  implicit class FullOps(private val full: Full) extends AnyVal {
    def remapVersion(scalaVersion: String): String =
      scalaVersion
  }

}
