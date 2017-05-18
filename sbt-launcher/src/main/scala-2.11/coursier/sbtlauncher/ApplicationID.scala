package coursier.sbtlauncher

import java.io.File

final case class ApplicationID(
  groupID: String,
  name: String,
  version: String,
  mainClass: String,
  mainComponents: Array[String],
  crossVersioned: Boolean,
  crossVersionedValue: xsbti.CrossValue,
  classpathExtra: Array[File]
) extends xsbti.ApplicationID {

  assert(crossVersioned == (crossVersionedValue != xsbti.CrossValue.Disabled))

  def disableCrossVersion(scalaVersion: String): ApplicationID =
    crossVersionedValue match {
      case xsbti.CrossValue.Disabled =>
        this
      case xsbti.CrossValue.Binary =>
        val scalaBinaryVersion = scalaVersion.split('.').take(2).mkString(".")
        copy(
          crossVersioned = false,
          crossVersionedValue = xsbti.CrossValue.Disabled,
          version = s"${version}_$scalaBinaryVersion"
        )
      case xsbti.CrossValue.Full =>
        copy(
          crossVersioned = false,
          crossVersionedValue = xsbti.CrossValue.Disabled,
          version = s"${version}_$scalaVersion"
        )
    }
}

object ApplicationID {
  def apply(id: xsbti.ApplicationID): ApplicationID =
    id match {
      case id0: ApplicationID => id0
      case _ =>
        ApplicationID(
          id.groupID(),
          id.name(),
          id.version(),
          id.mainClass(),
          id.mainComponents(),
          id.crossVersionedValue() != xsbti.CrossValue.Disabled,
          id.crossVersionedValue(),
          id.classpathExtra()
        )
    }
}
