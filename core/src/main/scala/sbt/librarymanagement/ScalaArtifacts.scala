package sbt.librarymanagement

object ScalaArtifacts {
  val Organization = "org.scala-lang"
  val LibraryID = "scala-library"
  val CompilerID = "scala-compiler"
  val ReflectID = "scala-reflect"
  val ActorsID = "scala-actors"
  val ScalapID = "scalap"
  val Artifacts = Vector(LibraryID, CompilerID, ReflectID, ActorsID, ScalapID)

  val Scala3LibraryID = "scala3-library"
  val Scala3CompilerID = "scala3-compiler"

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
