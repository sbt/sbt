package sbt.librarymanagement

object ScalaArtifacts {
  final val Organization = "org.scala-lang"
  final val LibraryID = "scala-library"
  final val CompilerID = "scala-compiler"
  final val ReflectID = "scala-reflect"
  final val ActorsID = "scala-actors"
  final val ScalapID = "scalap"
  final val Artifacts = Vector(LibraryID, CompilerID, ReflectID, ActorsID, ScalapID)

  final val Scala3LibraryID = "scala3-library"
  final val Scala3CompilerID = "scala3-compiler"
  final val Scala3InterfacesID = "scala3-interfaces"
  final val TastyCoreID = "tasty-core"

  private[sbt] final val Scala3LibraryPrefix = Scala3LibraryID + "_"
  private[sbt] final val Scala3CompilerPrefix = Scala3CompilerID + "_"
  private[sbt] final val TastyCorePrefix = TastyCoreID + "_"

  def isScala2Artifact(name: String): Boolean = {
    name == LibraryID || name == CompilerID || name == ReflectID || name == ActorsID || name == ScalapID
  }
  def isScala3Artifact(name: String): Boolean = {
    name.startsWith(Scala3LibraryPrefix) || name.startsWith(Scala3CompilerPrefix) ||
    name.startsWith(TastyCorePrefix) || name == Scala3InterfacesID
  }

  def isScala3(scalaVersion: String): Boolean = scalaVersion.startsWith("3.")

  def libraryIds(version: String): Array[String] = {
    if (isScala3(version))
      Array(Scala3LibraryID, LibraryID)
    else Array(LibraryID)
  }

  def compilerId(version: String): String = {
    if (isScala3(version)) Scala3CompilerID
    else CompilerID
  }

  def libraryDependency(version: String): ModuleID = libraryDependency(Organization, version)

  def libraryDependency(org: String, version: String): ModuleID = {
    if (isScala3(version))
      ModuleID(org, Scala3LibraryID, version).withCrossVersion(CrossVersion.binary)
    else
      ModuleID(org, LibraryID, version)
  }

  private[sbt] def toolDependencies(
      org: String,
      version: String
  ): Seq[ModuleID] =
    if (isScala3(version))
      Seq(
        ModuleID(org, Scala3CompilerID, version)
          .withConfigurations(Some(Configurations.ScalaTool.name + "->default(compile)"))
          .withCrossVersion(CrossVersion.binary)
      )
    else
      Seq(
        scalaToolDependency(org, CompilerID, version),
        scalaToolDependency(org, LibraryID, version)
      )

  private[this] def scalaToolDependency(org: String, id: String, version: String): ModuleID =
    ModuleID(org, id, version).withConfigurations(
      Some(Configurations.ScalaTool.name + "->default,optional(default)")
    )
}

object SbtArtifacts {
  val Organization = "org.scala-sbt"
}
